package com.example.billingms.interfaces.rest;

import com.example.billingms.config.StripeWebhookProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentGatewayWebhookController の単体テスト（IT9 A1.1 / ADR-0020 / US26）。
 *
 * <p>HMAC 署名検証の正常系 / 異常系を Stripe SDK の Webhook.constructEvent 経路で検証する。
 * SignatureVerificationException → 401、署名ヘッダ欠落 → 401、正常な署名 → 200 OK。</p>
 */
class PaymentGatewayWebhookControllerTest {

    private static final String SIGNING_SECRET = "whsec_test_secret_123456789012345678901234";
    private static final String VALID_PAYLOAD = """
            {"id":"evt_test_001","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"pi_test_001"}}}
            """.trim();

    private PaymentGatewayWebhookController controller() {
        StripeWebhookProperties properties = new StripeWebhookProperties(SIGNING_SECRET, 300L);
        return new PaymentGatewayWebhookController(properties);
    }

    @Test
    void 正常な署名と正しいペイロードを受信すると成功応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String signature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);

        ResponseEntity<String> response = controller().receive(VALID_PAYLOAD, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void 署名ヘッダが欠落していると認証失敗応答を返す() {
        ResponseEntity<String> response = controller().receive(VALID_PAYLOAD, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 不正な署名を受信すると認証失敗応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String tamperedSignature = "t=" + timestamp + ",v1=0000000000000000000000000000000000000000000000000000000000000000";

        ResponseEntity<String> response = controller().receive(VALID_PAYLOAD, tamperedSignature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ペイロードが改ざんされている場合は認証失敗応答を返す() {
        long timestamp = Instant.now().getEpochSecond();
        String validSignature = buildStripeSignatureHeader(timestamp, VALID_PAYLOAD, SIGNING_SECRET);
        String tamperedPayload = VALID_PAYLOAD.replace("pi_test_001", "pi_tampered_999");

        ResponseEntity<String> response = controller().receive(tamperedPayload, validSignature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void シークレットが空の場合は機能無効として利用不可応答を返す() {
        StripeWebhookProperties disabled = new StripeWebhookProperties("", 300L);
        PaymentGatewayWebhookController disabledController = new PaymentGatewayWebhookController(disabled);

        ResponseEntity<String> response = disabledController.receive(VALID_PAYLOAD, "t=1,v1=deadbeef");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
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
