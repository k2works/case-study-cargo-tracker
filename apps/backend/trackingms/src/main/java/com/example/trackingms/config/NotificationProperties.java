package com.example.trackingms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知設定（IT8 T3.1 / ADR-0018、SendGrid Dynamic Templates）。
 *
 * <p>{@code notification.adapter} で実装を切替可能（Logging / SendGrid）。
 * {@code notification.sendgrid.apiKey} と各テンプレート ID を Heroku Config Vars で管理する。</p>
 *
 * @param adapter   "logging"（デフォルト、開発/テスト）または "sendgrid"（本番）
 * @param sendgrid  SendGrid 設定（adapter=sendgrid 時に必須）
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        String adapter,
        SendGrid sendgrid
) {

    public NotificationProperties {
        if (adapter == null || adapter.isBlank()) {
            adapter = "logging";
        }
    }

    /**
     * SendGrid 設定。
     *
     * @param apiKey     SendGrid API key（環境変数 SENDGRID_API_KEY 推奨）
     * @param fromEmail  送信元メールアドレス
     * @param fromName   送信者名（任意、デフォルトは空）
     * @param templates  Dynamic Template ID 一覧
     */
    public record SendGrid(
            String apiKey,
            String fromEmail,
            String fromName,
            Templates templates
    ) {
        public SendGrid {
            if (fromName == null) {
                fromName = "";
            }
        }
    }

    /**
     * trackingms 6 種 + billingms 3 種の Dynamic Template ID。
     */
    public record Templates(
            String trackingIssued,
            String statusChanged,
            String misrouted,
            String exceptionRegistered,
            String exceptionResolved,
            String exceptionEscalation
    ) {
    }
}
