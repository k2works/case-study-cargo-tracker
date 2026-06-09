package com.example.billingms.interfaces.rest;

import com.example.billingms.application.StripeEventTranslator;
import com.example.billingms.config.StripeWebhookProperties;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * HMAC tolerance 境界値テスト（IT9 レビュー H6 / IT10 A3.7 / US32 / ADR-0020）。
 *
 * <p>{@link PaymentGatewayWebhookController} に Clock を注入し、現在時刻を固定した
 * 状態で 299s / 300s / 301s 古い timestamp を提示した際の挙動を実証する。</p>
 *
 * <p>tolerance = 300s（Stripe 推奨デフォルト）で:</p>
 * <ul>
 *   <li>skew = 299s → 通過（後続の Stripe SDK で署名検証へ進む）</li>
 *   <li>skew = 300s → 通過（境界、tolerance 「以下」のため受理）</li>
 *   <li>skew = 301s → 401 timestamp out of tolerance</li>
 * </ul>
 *
 * <p>Stripe SDK 内部の HMAC 検証ロジックは Clock 注入できないため、本テストは
 * Controller の前段 tolerance チェック単体を検証する。実 Stripe SDK との結合
 * 動作は {@link PaymentGatewayWebhookIntegrationTest} でカバーする。</p>
 */
class PaymentGatewayWebhookToleranceBoundaryTest {

    private static final String SIGNING_SECRET = "whsec_test_secret_123456789012345678901234";
    private static final long TOLERANCE = 300L;
    private static final Instant FIXED_NOW = Instant.parse("2026-06-09T12:00:00Z");

    private StripeWebhookProperties properties;
    private WebhookProcessedMapper webhookProcessedMapper;
    private StripeEventTranslator translator;
    private CommandGateway commandGateway;
    private Clock fixedClock;
    private PaymentGatewayWebhookController controller;

    @BeforeEach
    void setUp() {
        properties = new StripeWebhookProperties(SIGNING_SECRET, TOLERANCE);
        webhookProcessedMapper = mock(WebhookProcessedMapper.class);
        translator = mock(StripeEventTranslator.class);
        commandGateway = mock(CommandGateway.class);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        controller = new PaymentGatewayWebhookController(
                properties, webhookProcessedMapper, translator, commandGateway, fixedClock);
    }

    @Test
    @DisplayName("skew 299s（tolerance 300s 未満）は tolerance チェックを通過する")
    void skew299s_passesTolerance() {
        long timestamp = FIXED_NOW.getEpochSecond() - 299;
        String payload = "{\"id\":\"evt_boundary_299\",\"type\":\"payment_intent.succeeded\"}";
        String signature = buildSignature(timestamp, payload);

        ResponseEntity<String> response = controller.receive(payload, signature);

        assertThat(response.getStatusCode())
                .as("skew 299s は通過し、後続処理（HMAC 検証以降）に進むため timestamp 由来の 401 にはならない")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("skew 300s（tolerance と一致）は通過する（tolerance「以下」）")
    void skew300s_passesAtBoundary() {
        long timestamp = FIXED_NOW.getEpochSecond() - 300;
        String payload = "{\"id\":\"evt_boundary_300\",\"type\":\"payment_intent.succeeded\"}";
        String signature = buildSignature(timestamp, payload);

        ResponseEntity<String> response = controller.receive(payload, signature);

        assertThat(response.getStatusCode())
                .as("skew 300s（境界値）は通過する")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("skew 301s（tolerance 超過）は 401 timestamp out of tolerance を返す")
    void skew301s_rejectsAsOutOfTolerance() {
        long timestamp = FIXED_NOW.getEpochSecond() - 301;
        String payload = "{\"id\":\"evt_boundary_301\",\"type\":\"payment_intent.succeeded\"}";
        String signature = buildSignature(timestamp, payload);

        ResponseEntity<String> response = controller.receive(payload, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("timestamp out of tolerance");
    }

    @Test
    @DisplayName("未来側 skew 301s も同様に 401 で拒否される（abs(skew) > tolerance）")
    void futureSkew301s_rejectsAsOutOfTolerance() {
        long timestamp = FIXED_NOW.getEpochSecond() + 301;
        String payload = "{\"id\":\"evt_boundary_future_301\",\"type\":\"payment_intent.succeeded\"}";
        String signature = buildSignature(timestamp, payload);

        ResponseEntity<String> response = controller.receive(payload, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("timestamp out of tolerance");
    }

    @Test
    @DisplayName("extractTimestamp はカンマ区切りヘッダから t= を抽出する")
    void extractTimestamp_parsesStandardHeader() {
        Optional<Long> ts = PaymentGatewayWebhookController.extractTimestamp(
                "t=1700000000,v1=abc,v0=xyz");
        assertThat(ts).contains(1700000000L);
    }

    @Test
    @DisplayName("extractTimestamp は t= が無いヘッダで empty を返す")
    void extractTimestamp_returnsEmptyWhenNoTimestamp() {
        Optional<Long> ts = PaymentGatewayWebhookController.extractTimestamp("v1=abc");
        assertThat(ts).isEmpty();
    }

    private static String buildSignature(long timestamp, String payload) {
        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
