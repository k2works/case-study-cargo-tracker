package com.example.billingms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * billingms の運用パラメータ（IT7 review 中対応、ハードコード除去）。
 *
 * <p>請求書発行から督促までの業務パラメータを一元管理する。IT8 で契約別支払サイト
 * （NET30 / NET60 / NET90）対応時は本クラスを {@code Map<ShipperType, Integer>} 等に
 * 拡張する。</p>
 *
 * @param paymentDueDays      支払期限の日数（発行日 + N 日）。デフォルト 30
 * @param overdue             督促スケジューラ設定
 * @param discountDescription DISCOUNT 行の description テンプレート（{@code %d} で割引率を埋める）
 */
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        int paymentDueDays,
        Overdue overdue,
        String discountDescription
) {

    public BillingProperties {
        if (paymentDueDays <= 0) {
            throw new IllegalArgumentException("paymentDueDays は 1 以上である必要があります: " + paymentDueDays);
        }
        if (overdue == null) {
            throw new IllegalArgumentException("overdue 設定は必須です");
        }
        if (discountDescription == null || discountDescription.isBlank()) {
            throw new IllegalArgumentException("discountDescription は必須です");
        }
    }

    /**
     * OverdueScheduler の cron / timezone 設定。
     *
     * @param cron @Scheduled cron 式
     * @param zone IANA タイムゾーン名（{@code Asia/Tokyo} 等）
     */
    public record Overdue(String cron, String zone) {
        public Overdue {
            if (cron == null || cron.isBlank()) {
                throw new IllegalArgumentException("overdue.cron は必須です");
            }
            if (zone == null || zone.isBlank()) {
                throw new IllegalArgumentException("overdue.zone は必須です");
            }
        }
    }
}
