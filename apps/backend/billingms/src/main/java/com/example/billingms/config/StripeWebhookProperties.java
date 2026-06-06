package com.example.billingms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe webhook 受信設定（IT9 A1 / ADR-0020）。
 *
 * <p>{@code billing.stripe-webhook.signing-secret} で Stripe ダッシュボード発行の HMAC 検証鍵を保持する。
 * 本番（Heroku）では Config Vars 経由、ローカルでは {@code stripe listen} 等で発行される whsec_xxx を設定する。</p>
 *
 * <p>{@code tolerance-seconds} は Stripe Webhook.constructEvent に渡す許容クロックスキュー（秒）。
 * デフォルト 300 秒（Stripe 推奨値）。</p>
 *
 * @param signingSecret    HMAC 検証用 secret（whsec_xxx）。null/blank の場合は webhook 受信 disable
 * @param toleranceSeconds 検証時の許容クロックスキュー（秒）
 */
@ConfigurationProperties(prefix = "billing.stripe-webhook")
public record StripeWebhookProperties(
        String signingSecret,
        long toleranceSeconds
) {

    public StripeWebhookProperties {
        if (toleranceSeconds <= 0) {
            toleranceSeconds = 300L;
        }
    }
}
