package com.example.billingms.interfaces.rest;

import com.example.billingms.config.StripeWebhookProperties;
import com.example.billingms.domain.projections.WebhookProcessed;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

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
            {"id":"evt_test_001","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"pi_test_001"}}}
            """.trim();

    private WebhookProcessedMapper mapper;
    private PaymentGatewayWebhookController controller;

    @BeforeEach
    void setup() {
        mapper = mock(WebhookProcessedMapper.class);
        StripeWebhookProperties properties = new StripeWebhookProperties(SIGNING_SECRET, 300L);
        controller = new PaymentGatewayWebhookController(properties, mapper);
    }

    @Test
    void 新規受信時は冪等性テーブルに記録され成功応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);
        when(mapper.findByEventId("evt_test_001")).thenReturn(null);

        ResponseEntity<String> response = controller.receive(VALID_PAYLOAD, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("received");
        verify(mapper, times(1)).insertReceived(
                eq("evt_test_001"), eq("STRIPE"), eq("payment_intent.succeeded"), anyString());
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
    void シークレットが空の場合は機能無効として利用不可応答を返す() {
        StripeWebhookProperties disabled = new StripeWebhookProperties("", 300L);
        PaymentGatewayWebhookController disabledController = new PaymentGatewayWebhookController(disabled, mapper);

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
