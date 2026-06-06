package com.example.billingms.interfaces.rest;

import com.example.billingms.config.StripeWebhookProperties;
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

/**
 * Stripe webhook 受信エンドポイント（IT9 A1.1 / ADR-0020 / US26）。
 *
 * <p>Stripe ダッシュボードから配信される webhook を受信し、HMAC 署名検証後に
 * Event オブジェクトを取り出して処理を引き渡す。本タスク（A1.1）では HMAC 検証 + 200 OK 応答までを
 * 実装し、idempotency キー記録（A1.2）/ Aggregate コマンド発火（A1.4）は次のタスクで実装する。</p>
 *
 * <p>応答ステータス:</p>
 * <ul>
 *   <li>200 OK: 検証成功（後続タスクで処理結果に応じた拡張あり）</li>
 *   <li>401 Unauthorized: 署名ヘッダ欠落または HMAC 検証失敗</li>
 *   <li>503 Service Unavailable: signing-secret 未設定（環境変数欠落時）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/billing/webhooks/stripe")
public class PaymentGatewayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayWebhookController.class);

    private final StripeWebhookProperties properties;

    public PaymentGatewayWebhookController(StripeWebhookProperties properties) {
        this.properties = properties;
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
        try {
            Event event = Webhook.constructEvent(
                    payload,
                    signature,
                    properties.signingSecret(),
                    properties.toleranceSeconds()
            );
            log.info("Stripe webhook 受信成功: event_id={}, type={}", event.getId(), event.getType());
            return ResponseEntity.ok("received");
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook 署名検証失敗: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
    }
}
