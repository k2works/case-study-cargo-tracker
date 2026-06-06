package com.example.billingms.application;

import com.example.billingms.domain.commands.RecordPartialPaymentCommand;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe Event を {@link RecordPartialPaymentCommand} に変換するアプリケーションサービス
 * （IT9 A1.5 / ADR-0020 / US26）。
 *
 * <p>PaymentIntent.metadata で invoice_id / paid_amount / currency を渡す Stripe 利用パターンを
 * 前提とする。Stripe SDK の Event オブジェクトは type 確認に、raw payload は metadata 抽出に使う。
 * これにより SDK の apiVersion 依存挙動から独立する。</p>
 *
 * <p>受け付ける event type: {@code payment_intent.succeeded}。それ以外は {@link Optional#empty()}。</p>
 */
@Component
public class StripeEventTranslator {

    private static final Logger log = LoggerFactory.getLogger(StripeEventTranslator.class);
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String META_INVOICE_ID = "invoice_id";
    private static final String META_PAID_AMOUNT = "paid_amount";
    private static final String META_CURRENCY = "currency";
    private static final String STRIPE_PAYMENT_METHOD = "STRIPE";

    private final Clock clock;

    public StripeEventTranslator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Stripe Event と raw payload を解析して RecordPartialPaymentCommand を構築する。
     * 必須 metadata が欠落している場合は空を返し、Webhook Controller が markFailed する。
     */
    public Optional<RecordPartialPaymentCommand> translate(Event event, String rawPayload) {
        if (!PAYMENT_INTENT_SUCCEEDED.equals(event.getType())) {
            log.debug("Stripe event type {} は対象外（payment_intent.succeeded のみ処理）", event.getType());
            return Optional.empty();
        }
        JsonObject paymentIntent = extractPaymentIntent(rawPayload);
        if (paymentIntent == null) {
            log.warn("Stripe event payload から PaymentIntent オブジェクト抽出失敗: event_id={}", event.getId());
            return Optional.empty();
        }
        JsonObject metadata = nestedObject(paymentIntent, "metadata");
        if (metadata == null) {
            log.warn("Stripe PaymentIntent.metadata が欠落: event_id={}", event.getId());
            return Optional.empty();
        }
        String invoiceId = stringValue(metadata, META_INVOICE_ID);
        String paidAmountStr = stringValue(metadata, META_PAID_AMOUNT);
        String currency = stringValue(metadata, META_CURRENCY);
        if (isBlank(invoiceId) || isBlank(paidAmountStr) || isBlank(currency)) {
            log.warn("Stripe PaymentIntent.metadata に必須項目（invoice_id/paid_amount/currency）が欠落: event_id={}",
                    event.getId());
            return Optional.empty();
        }
        BigDecimal paidAmount;
        try {
            paidAmount = new BigDecimal(paidAmountStr);
        } catch (NumberFormatException e) {
            log.warn("Stripe PaymentIntent.metadata.paid_amount の数値変換失敗: event_id={}, value={}",
                    event.getId(), paidAmountStr);
            return Optional.empty();
        }
        String paymentIntentId = stringValue(paymentIntent, "id");
        LocalDateTime paidAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(event.getCreated()), ZoneOffset.UTC);
        return Optional.of(new RecordPartialPaymentCommand(
                invoiceId,
                UUID.randomUUID().toString(),
                paidAmount,
                currency,
                paidAt,
                STRIPE_PAYMENT_METHOD,
                paymentIntentId
        ));
    }

    private static JsonObject extractPaymentIntent(String rawPayload) {
        try {
            JsonElement root = JsonParser.parseString(rawPayload);
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            JsonObject data = nestedObject(root.getAsJsonObject(), "data");
            if (data == null) {
                return null;
            }
            return nestedObject(data, "object");
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static JsonObject nestedObject(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        return el.getAsJsonObject();
    }

    private static String stringValue(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
