package com.example.billingms.interfaces.rest;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.model.TransportRecord;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.domain.projections.Payment;
import com.example.billingms.domain.projections.WebhookProcessed;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import com.example.billingms.infrastructure.repositories.mybatis.PaymentMapper;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe webhook 統合テスト（IT9 A1.6 / ADR-0020 / US26、IT10 A3.6 で分割）。
 *
 * <p>@SpringBootTest で billingms 全体を起動し、Webhook 受信 → HMAC 検証 → 冪等性チェック →
 * Invoice 集約 → Projection 反映までの E2E フローを実 HTTP（MockMvc）で検証する。</p>
 *
 * <p>シナリオ別メソッド構成（IT9 レビュー H5 / IT10 A3.6）:</p>
 * <ul>
 *   <li>{@link #部分入金webhookでPARTIALLY_PAIDに遷移し投影に反映される()}</li>
 *   <li>{@link #同一event_id再送は冪等で副作用が発生しない()}</li>
 *   <li>{@link #残額入金webhookでPAIDに遷移する()}</li>
 *   <li>{@link #不正な署名のwebhookは401で拒否され投影に影響しない()}</li>
 * </ul>
 *
 * <p>local-h2 + Axon subscribing processor のため Webhook 受信から Projection 反映までは同期的だが、
 * EventBus dispatch の僅かな遅延を Awaitility で吸収する（atMost 5s に短縮）。各メソッドは UUID で
 * Invoice ID を独立採番するため、共有 Spring Context 上でも相互干渉しない。
 * {@link DirtiesContext} はクラス起動時のみ webhook secret を application properties で固定するため。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "billing.stripe-webhook.signing-secret=whsec_test_secret_123456789012345678901234",
                "billing.stripe-webhook.tolerance-seconds=300",
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false",
                "axon.eventhandling.processors.local-billing.mode=subscribing",
                "axon.eventhandling.processors.cross-billing.mode=subscribing",
                "axon.eventhandling.processors.outbound-billing-notification.mode=subscribing",
                "app.dev-seed.enabled=false"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PaymentGatewayWebhookIntegrationTest {

    private static final String SIGNING_SECRET = "whsec_test_secret_123456789012345678901234";
    private static final String WEBHOOK_PATH = "/api/v1/billing/webhooks/stripe";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CommandGateway commandGateway;
    @Autowired
    private InvoiceSummaryMapper invoiceMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private WebhookProcessedMapper webhookMapper;

    @Test
    void 部分入金webhookでPARTIALLY_PAIDに遷移し投影に反映される() throws Exception {
        InvoicedFixture fx = arrangeInvoicedInvoice();
        BigDecimal partialAmount = fx.totalAmount.divide(new BigDecimal("2"));

        String eventId = newEventId();
        String payload = buildPayload(eventId, fx.invoiceId, partialAmount, "pi_it_partial");
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, payload, SIGNING_SECRET);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        await().atMost(AWAIT_TIMEOUT).until(() ->
                "PARTIALLY_PAID".equals(invoiceMapper.findByInvoiceId(fx.invoiceId).getBillingStatus()));

        WebhookProcessed processed = webhookMapper.findByEventId(eventId);
        assertThat(processed).isNotNull();
        assertThat(processed.getProcessingStatus()).isEqualTo("PROCESSED");

        InvoiceSummary partial = invoiceMapper.findByInvoiceId(fx.invoiceId);
        assertThat(partial.getBillingStatus()).isEqualTo("PARTIALLY_PAID");

        List<Payment> payments = paymentMapper.findByInvoiceId(fx.invoiceId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getPaidAmount()).isEqualByComparingTo(partialAmount);
    }

    @Test
    void 同一event_id再送は冪等で副作用が発生しない() throws Exception {
        InvoicedFixture fx = arrangeInvoicedInvoice();
        BigDecimal partialAmount = fx.totalAmount.divide(new BigDecimal("2"));

        String eventId = newEventId();
        String payload = buildPayload(eventId, fx.invoiceId, partialAmount, "pi_it_idemp");
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, payload, SIGNING_SECRET);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        await().atMost(AWAIT_TIMEOUT).until(() ->
                "PARTIALLY_PAID".equals(invoiceMapper.findByInvoiceId(fx.invoiceId).getBillingStatus()));
        assertThat(paymentMapper.findByInvoiceId(fx.invoiceId)).hasSize(1);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(paymentMapper.findByInvoiceId(fx.invoiceId))
                .as("同一 event_id 再送後も payment は 1 件のまま")
                .hasSize(1);
    }

    @Test
    void 残額入金webhookでPAIDに遷移する() throws Exception {
        InvoicedFixture fx = arrangeInvoicedInvoice();
        BigDecimal partialAmount = fx.totalAmount.divide(new BigDecimal("2"));

        sendWebhook(fx.invoiceId, partialAmount, "pi_it_remaining_1", newEventId());
        await().atMost(AWAIT_TIMEOUT).until(() ->
                "PARTIALLY_PAID".equals(invoiceMapper.findByInvoiceId(fx.invoiceId).getBillingStatus()));

        BigDecimal remaining = fx.totalAmount.subtract(partialAmount);
        sendWebhook(fx.invoiceId, remaining, "pi_it_remaining_2", newEventId());

        await().atMost(AWAIT_TIMEOUT).until(() ->
                "PAID".equals(invoiceMapper.findByInvoiceId(fx.invoiceId).getBillingStatus()));
        assertThat(paymentMapper.findByInvoiceId(fx.invoiceId)).hasSize(2);
    }

    @Test
    void 不正な署名のwebhookは401で拒否され投影に影響しない() throws Exception {
        InvoicedFixture fx = arrangeInvoicedInvoice();

        String eventId = "evt_it_bad_" + UUID.randomUUID().toString().substring(0, 8);
        String payload = buildPayload(eventId, fx.invoiceId, new BigDecimal("100"), "pi_bad");
        String invalidSig = "t=" + Instant.now().getEpochSecond()
                + ",v1=0000000000000000000000000000000000000000000000000000000000000000";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", invalidSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(webhookMapper.findByEventId(eventId)).isNull();
        assertThat(invoiceMapper.findByInvoiceId(fx.invoiceId).getBillingStatus()).isEqualTo("INVOICED");
    }

    private InvoicedFixture arrangeInvoicedInvoice() {
        String invoiceId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        String shipperId = UUID.randomUUID().toString();
        TransportRecord transport = new TransportRecord(
                new BigDecimal("1000"),
                new BigDecimal("500"),
                "GENERAL",
                2,
                "JPY"
        );
        commandGateway.sendAndWait(new CalculateInvoiceCommand(invoiceId, bookingId, shipperId, transport));
        commandGateway.sendAndWait(new IssueInvoiceCommand(invoiceId));
        await().atMost(AWAIT_TIMEOUT).until(() -> {
            InvoiceSummary s = invoiceMapper.findByInvoiceId(invoiceId);
            return s != null && "INVOICED".equals(s.getBillingStatus());
        });
        InvoiceSummary issued = invoiceMapper.findByInvoiceId(invoiceId);
        return new InvoicedFixture(invoiceId, issued.getTotalAmount());
    }

    private void sendWebhook(String invoiceId, BigDecimal amount, String paymentIntentId, String eventId)
            throws Exception {
        String payload = buildPayload(eventId, invoiceId, amount, paymentIntentId);
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, payload, SIGNING_SECRET);
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private static String newEventId() {
        return "evt_it_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String buildPayload(String eventId, String invoiceId, BigDecimal amount, String pi) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2024-11-20.acacia",
                  "type": "payment_intent.succeeded",
                  "created": %d,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "metadata": {
                        "invoice_id": "%s",
                        "paid_amount": "%s",
                        "currency": "JPY"
                      }
                    }
                  }
                }
                """.formatted(eventId, Instant.now().getEpochSecond(), pi, invoiceId, amount.toPlainString());
    }

    private static String buildStripeSignatureHeader(long timestamp, String payload, String secret) {
        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record InvoicedFixture(String invoiceId, BigDecimal totalAmount) {
    }
}
