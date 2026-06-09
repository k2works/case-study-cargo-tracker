package com.example.billingms.interfaces.rest;

import com.example.billingms.application.StripeEventTranslator;
import com.example.billingms.config.StripeWebhookProperties;
import com.example.billingms.domain.projections.WebhookProcessed;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentGatewayWebhookController の単体テスト（IT9 A1.1 + A1.2 / ADR-0020 / US26）。
 *
 * <p>HMAC 署名検証 + 冪等性ガード（Stripe Event ID）を検証する。</p>
 */
class PaymentGatewayWebhookControllerTest {

    private static final String SIGNING_SECRET = "whsec_test_secret_123456789012345678901234";
    private static final String VALID_PAYLOAD = """
            {"id":"evt_test_001","object":"event","api_version":"2024-11-20.acacia","type":"payment_intent.succeeded","data":{"object":{"id":"pi_test_001","object":"payment_intent"}}}
            """.trim();

    private WebhookProcessedMapper mapper;
    private StripeEventTranslator translator;
    private CommandGateway commandGateway;
    private PaymentGatewayWebhookController controller;

    @BeforeEach
    void setup() {
        mapper = mock(WebhookProcessedMapper.class);
        translator = new StripeEventTranslator(Clock.systemUTC());
        commandGateway = mock(CommandGateway.class);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        StripeWebhookProperties properties = new StripeWebhookProperties(SIGNING_SECRET, 300L);
        controller = new PaymentGatewayWebhookController(properties, mapper, translator, commandGateway, Clock.systemUTC());
    }

    @Test
    void 新規受信時は冪等性テーブルに記録され成功応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);
        when(mapper.findByEventId("evt_test_001")).thenReturn(null);

        ResponseEntity<String> response = controller.receive(VALID_PAYLOAD, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // payload に PaymentIntent metadata がないため、translator が empty を返し skipped となる
        assertThat(response.getBody()).isEqualTo("skipped");
        verify(mapper, times(1)).insertReceived(
                eq("evt_test_001"), eq("STRIPE"), eq("payment_intent.succeeded"), anyString());
        verify(mapper, times(1)).markFailed(eq("evt_test_001"), anyString());
    }

    @Test
    void 同一イベント識別子の再送は副作用なく既処理として受理する() {
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);
        WebhookProcessed existing = new WebhookProcessed();
        existing.setEventId("evt_test_001");
        existing.setProcessingStatus("PROCESSED");
        when(mapper.findByEventId("evt_test_001")).thenReturn(existing);

        ResponseEntity<String> response = controller.receive(VALID_PAYLOAD, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("already processed");
        verify(mapper, never()).insertReceived(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 署名ヘッダが欠落していると認証失敗応答を返す() {
        ResponseEntity<String> response = controller.receive(VALID_PAYLOAD, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mapper, never()).findByEventId(anyString());
    }

    @Test
    void 不正な署名を受信すると認証失敗応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String tamperedSignature = "t=" + timestamp + ",v1=0000000000000000000000000000000000000000000000000000000000000000";

        ResponseEntity<String> response = controller.receive(VALID_PAYLOAD, tamperedSignature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mapper, never()).insertReceived(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ペイロードが改ざんされている場合は認証失敗応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String validSignature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);
        String tamperedPayload = VALID_PAYLOAD.replace("pi_test_001", "pi_tampered_999");

        ResponseEntity<String> response = controller.receive(tamperedPayload, validSignature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mapper, never()).insertReceived(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 完全な部分入金ペイロードはコマンド発火と完了マークに到達する() {
        String fullPayload = """
                {"id":"evt_full_001","object":"event","api_version":"2024-11-20.acacia","type":"payment_intent.succeeded","created":1735000000,"data":{"object":{"id":"pi_full","object":"payment_intent","metadata":{"invoice_id":"INV-FULL-001","paid_amount":"50000","currency":"JPY"}}}}
                """.trim();
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, fullPayload, SIGNING_SECRET);
        when(mapper.findByEventId("evt_full_001")).thenReturn(null);

        ResponseEntity<String> response = controller.receive(fullPayload, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("processed");
        verify(commandGateway, times(1)).send(any());
        verify(mapper, times(1)).markProcessed(eq("evt_full_001"), eq("INV-FULL-001"));
    }

    @Test
    void US26対象外イベントcharge_refundedは200_skippedで受理しmarkFailedされる() {
        String refundPayload = """
                {"id":"evt_refund_001","object":"event","api_version":"2024-11-20.acacia",
                 "type":"charge.refunded","data":{"object":{"id":"ch_refund_001","object":"charge"}}}
                """.trim();
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, refundPayload, SIGNING_SECRET);
        when(mapper.findByEventId("evt_refund_001")).thenReturn(null);

        ResponseEntity<String> response = controller.receive(refundPayload, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("skipped");
        verify(mapper, times(1)).markFailed(eq("evt_refund_001"),
                eq("unsupported event type or missing metadata"));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void US26対象外イベントcharge_dispute_createdは200_skippedで受理しmarkFailedされる() {
        String disputePayload = """
                {"id":"evt_dispute_001","object":"event","api_version":"2024-11-20.acacia",
                 "type":"charge.dispute.created","data":{"object":{"id":"dp_dispute_001","object":"dispute"}}}
                """.trim();
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, disputePayload, SIGNING_SECRET);
        when(mapper.findByEventId("evt_dispute_001")).thenReturn(null);

        ResponseEntity<String> response = controller.receive(disputePayload, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("skipped");
        verify(mapper, times(1)).markFailed(eq("evt_dispute_001"),
                eq("unsupported event type or missing metadata"));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void シークレットが空の場合は機能無効として利用不可応答を返す() {
        StripeWebhookProperties disabled = new StripeWebhookProperties("", 300L);
        PaymentGatewayWebhookController disabledController = new PaymentGatewayWebhookController(
                disabled, mapper, translator, commandGateway, Clock.systemUTC());

        ResponseEntity<String> response = disabledController.receive(VALID_PAYLOAD, "t=1,v1=deadbeef");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(mapper, never()).findByEventId(anyString());
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    /**
     * Stripe 仕様の Signature ヘッダを生成する：{@code t=<timestamp>,v1=<HMAC SHA-256 of "<timestamp>.<payload>">}.
     */
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
