package com.example.billingms.interfaces.rest;

import com.example.billingms.application.StripeEventTranslator;
import com.example.billingms.config.StripeWebhookProperties;
import com.example.billingms.domain.commands.RecordPartialPaymentCommand;
import com.example.billingms.infrastructure.repositories.mybatis.WebhookProcessedMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.axonframework.commandhandling.gateway.CommandGateway;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Stripe webhook 受信エンドポイント（IT9 A1.1 + A1.2 + A1.5a / ADR-0020 / US26）。
 *
 * <p>Stripe ダッシュボードから配信される webhook を受信し、以下の順で処理する:</p>
 * <ol>
 *   <li>HMAC 署名検証（A1.1）</li>
 *   <li>Stripe Event ID で冪等性チェック（A1.2）</li>
 *   <li>webhook_processed に RECEIVED で記録</li>
 *   <li>StripeEventTranslator で RecordPartialPaymentCommand を構築（A1.5a）</li>
 *   <li>CommandGateway 経由で Invoice 集約に発火</li>
 *   <li>成功 → markProcessed、失敗 → markFailed</li>
 * </ol>
 *
 * <p>応答ステータス:</p>
 * <ul>
 *   <li>200 OK: 新規受信成功、同一 event の再送（副作用なく既処理として受理）、
 *       または対象外 event type（payment_intent.succeeded 以外）</li>
 *   <li>401 Unauthorized: 署名ヘッダ欠落または HMAC 検証失敗</li>
 *   <li>503 Service Unavailable: signing-secret 未設定</li>
 * </ul>
 *
 * <p>業務エラー（残額超過 / 状態不一致 / 通貨不一致 等）でも HTTP は 200 で返す。Stripe 側の
 * retry を抑制するため。エラー詳細は webhook_processed.error_reason に記録する。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/webhooks/stripe")
public class PaymentGatewayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayWebhookController.class);
    private static final String PROVIDER_STRIPE = "STRIPE";
    private static final long COMMAND_TIMEOUT_SEC = 5L;

    private final StripeWebhookProperties properties;
    private final WebhookProcessedMapper webhookProcessedMapper;
    private final StripeEventTranslator translator;
    private final CommandGateway commandGateway;

    public PaymentGatewayWebhookController(
            StripeWebhookProperties properties,
            WebhookProcessedMapper webhookProcessedMapper,
            StripeEventTranslator translator,
            CommandGateway commandGateway
    ) {
        this.properties = properties;
        this.webhookProcessedMapper = webhookProcessedMapper;
        this.translator = translator;
        this.commandGateway = commandGateway;
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

        Optional<RecordPartialPaymentCommand> commandOpt = translator.translate(event, payload);
        if (commandOpt.isEmpty()) {
            // 対象外 event type または metadata 不足。記録は残し副作用なし。
            webhookProcessedMapper.markFailed(event.getId(),
                    "unsupported event type or missing metadata");
            return ResponseEntity.ok("skipped");
        }

        RecordPartialPaymentCommand command = commandOpt.get();
        try {
            commandGateway.send(command).get(COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS);
            webhookProcessedMapper.markProcessed(event.getId(), command.invoiceId());
            return ResponseEntity.ok("processed");
        } catch (Exception e) {
            // 集約エラー（残額超過 / 状態不一致 / 通貨不一致 等）は markFailed して 200 で返す。
            // Stripe 側の retry を抑制し、エラーは webhook_processed.error_reason で監査する。
            String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Stripe webhook コマンド発火失敗: event_id={}, reason={}", event.getId(), reason);
            webhookProcessedMapper.markFailed(event.getId(), reason);
            return ResponseEntity.ok("failed");
        }
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
