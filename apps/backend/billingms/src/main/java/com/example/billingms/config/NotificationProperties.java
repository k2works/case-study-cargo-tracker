package com.example.billingms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知設定（IT8 T3.2 / ADR-0018、SendGrid Dynamic Templates）。
 *
 * <p>{@code notification.adapter} で実装を切替（logging / sendgrid）。
 * billingms 用は 3 種類のテンプレート（invoiceIssued / paymentReceived / overdue）。</p>
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
     * billingms 3 種の Dynamic Template ID。
     */
    public record Templates(
            String invoiceIssued,
            String paymentReceived,
            String overdue
    ) {
    }
}
