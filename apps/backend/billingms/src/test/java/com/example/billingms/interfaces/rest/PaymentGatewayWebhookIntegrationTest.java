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
 * Stripe webhook 統合テスト（IT9 A1.6 / ADR-0020 / US26）。
 *
 * <p>@SpringBootTest で billingms 全体を起動し、Webhook 受信 → HMAC 検証 → 冪等性チェック →
 * Invoice 集約 → Projection 反映までの一連の E2E フローを実 HTTP（MockMvc）で検証する。</p>
 *
 * <p>シナリオ:</p>
 * <ol>
 *   <li>事前準備: CalculateInvoice + Issue で INVOICED 状態の Invoice を作成</li>
 *   <li>Stripe webhook（部分入金）を POST → 200 processed + PARTIALLY_PAID + payment.is_partial=TRUE</li>
 *   <li>同一 event_id 再送 → 200 already processed（副作用なし）</li>
 *   <li>残額入金 webhook → 200 processed + PAID + paid_so_far=total_amount</li>
 * </ol>
 *
 * <p>local-h2 + Axon subscribing processor のため、Webhook 受信から Projection 反映までは同期的。
 * @DirtiesContext は Webhook secret の application properties override 競合回避用。</p>
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
    void Stripe部分入金webhookで集約と投影が反映される() throws Exception {
        // Arrange: CalculateInvoice + Issue で INVOICED 状態を作成
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
        // Axon EventHandler の async dispatch を待機
        await().atMost(Duration.ofSeconds(15)).until(() ->
                invoiceMapper.findByInvoiceId(invoiceId) != null
                        && "INVOICED".equals(invoiceMapper.findByInvoiceId(invoiceId).getBillingStatus()));
        InvoiceSummary issued = invoiceMapper.findByInvoiceId(invoiceId);
        BigDecimal totalAmount = issued.getTotalAmount();
        assertThat(issued.getBillingStatus()).isEqualTo("INVOICED");
        BigDecimal partialAmount = totalAmount.divide(new BigDecimal("2"));

        // Act 1: 部分入金 webhook
        String eventId = "evt_it_" + UUID.randomUUID().toString().substring(0, 8);
        String payload = buildPayload(eventId, invoiceId, partialAmount, "pi_it_1");
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, payload, SIGNING_SECRET);
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Assert 1: PARTIALLY_PAID + is_partial payment + webhook PROCESSED
        await().atMost(Duration.ofSeconds(15)).until(() ->
                "PARTIALLY_PAID".equals(invoiceMapper.findByInvoiceId(invoiceId).getBillingStatus()));
        WebhookProcessed processed = webhookMapper.findByEventId(eventId);
        assertThat(processed).isNotNull();
        assertThat(processed.getProcessingStatus()).isEqualTo("PROCESSED");
        InvoiceSummary partial = invoiceMapper.findByInvoiceId(invoiceId);
        assertThat(partial.getBillingStatus()).isEqualTo("PARTIALLY_PAID");
        List<Payment> payments = paymentMapper.findByInvoiceId(invoiceId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getPaidAmount()).isEqualByComparingTo(partialAmount);

        // Act 2: 同一 event_id 再送（冪等性）
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Assert 2: 投影は変わらない（payment 件数 1 件のまま）
        assertThat(paymentMapper.findByInvoiceId(invoiceId)).hasSize(1);

        // Act 3: 残額入金 webhook
        String eventId2 = "evt_it_" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal remaining = totalAmount.subtract(partialAmount);
        String payload2 = buildPayload(eventId2, invoiceId, remaining, "pi_it_2");
        long timestamp2 = Instant.now().getEpochSecond();
        String signature2 = buildStripeSignatureHeader(timestamp2, payload2, SIGNING_SECRET);
        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", signature2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isOk());

        // Assert 3: PAID + payments 2 件
        await().atMost(Duration.ofSeconds(15)).until(() ->
                "PAID".equals(invoiceMapper.findByInvoiceId(invoiceId).getBillingStatus()));
        InvoiceSummary paid = invoiceMapper.findByInvoiceId(invoiceId);
        assertThat(paid.getBillingStatus()).isEqualTo("PAID");
        assertThat(paymentMapper.findByInvoiceId(invoiceId)).hasSize(2);
    }

    @Test
    void 不正な署名のwebhookは401で拒否され投影に影響しない() throws Exception {
        String invoiceId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        String shipperId = UUID.randomUUID().toString();
        TransportRecord transport = new TransportRecord(
                new BigDecimal("1000"), new BigDecimal("500"), "GENERAL", 2, "JPY");
        commandGateway.sendAndWait(new CalculateInvoiceCommand(invoiceId, bookingId, shipperId, transport));
        commandGateway.sendAndWait(new IssueInvoiceCommand(invoiceId));
        await().atMost(Duration.ofSeconds(15)).until(() ->
                invoiceMapper.findByInvoiceId(invoiceId) != null
                        && "INVOICED".equals(invoiceMapper.findByInvoiceId(invoiceId).getBillingStatus()));

        String eventId = "evt_it_bad_" + UUID.randomUUID().toString().substring(0, 8);
        String payload = buildPayload(eventId, invoiceId, new BigDecimal("100"), "pi_bad");
        // 不正な署名
        String invalidSig = "t=" + Instant.now().getEpochSecond()
                + ",v1=0000000000000000000000000000000000000000000000000000000000000000";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header("Stripe-Signature", invalidSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        // webhook_processed には記録されない
        assertThat(webhookMapper.findByEventId(eventId)).isNull();
        // 状態は INVOICED のまま
        assertThat(invoiceMapper.findByInvoiceId(invoiceId).getBillingStatus()).isEqualTo("INVOICED");
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
}
