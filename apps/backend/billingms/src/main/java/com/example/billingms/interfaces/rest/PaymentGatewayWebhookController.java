package com.example.billingms.interfaces.rest;

import com.example.billingms.config.StripeWebhookProperties;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stripe webhook 受信エンドポイント（IT9 A1.1 + A1.2 / ADR-0020 / US26）。
 *
 * <p>Stripe ダッシュボードから配信される webhook を受信し、HMAC 署名検証後に
 * 冪等性キー（Stripe Event ID）で重複チェックを行い、新規受信を webhook_processed に記録する。
 * Aggregate コマンド発火（PARTIALLY_PAID 状態遷移）は A1.4 で実装する。</p>
 *
 * <p>応答ステータス:</p>
 * <ul>
 *   <li>200 OK: 新規受信成功、または同一 event の再送（副作用なく既処理として受理）</li>
 *   <li>401 Unauthorized: 署名ヘッダ欠落または HMAC 検証失敗</li>
 *   <li>503 Service Unavailable: signing-secret 未設定</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/billing/webhooks/stripe")
public class PaymentGatewayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayWebhookController.class);
    private static final String PROVIDER_STRIPE = "STRIPE";

    private final StripeWebhookProperties properties;
    private final WebhookProcessedMapper webhookProcessedMapper;

    public PaymentGatewayWebhookController(
            StripeWebhookProperties properties,
            WebhookProcessedMapper webhookProcessedMapper
    ) {
        this.properties = properties;
        this.webhookProcessedMapper = webhookProcessedMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receive(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (properties.signingSecret() == null || properties.signingSecret().isBlank()) {
            log.warn("Stripe webhook が呼ばれましたが billing.stripe-webhook.signing-secret が未設定です");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("stripe webhook disabled");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Stripe webhook 署名ヘッダ Stripe-Signature が欠落しています");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("missing signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(
                    payload,
                    signature,
                    properties.signingSecret(),
                    properties.toleranceSeconds()
            );
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook 署名検証失敗: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        if (webhookProcessedMapper.findByEventId(event.getId()) != null) {
            log.info("Stripe webhook 冪等性ヒット: event_id={} を既処理として副作用なく受理", event.getId());
            return ResponseEntity.ok("already processed");
        }

        webhookProcessedMapper.insertReceived(
                event.getId(),
                PROVIDER_STRIPE,
                event.getType(),
                sha256(payload)
        );
        log.info("Stripe webhook 新規受信: event_id={}, type={}", event.getId(), event.getType());
        return ResponseEntity.ok("received");
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
