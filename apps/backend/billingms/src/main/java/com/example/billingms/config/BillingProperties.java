package com.example.billingms.config;

import com.example.billingms.domain.model.ShipperType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * billingms の運用パラメータ（IT7 review 中対応、ハードコード除去）。
 *
 * <p>請求書発行から督促までの業務パラメータを一元管理する。IT8 T1.9 で契約別支払サイト
 * （NET30 / NET60 / NET90）対応のため {@code paymentDueDaysByType} を追加。
 * 集約への組み込み（{@code shipperType} を Invoice 集約 state に保持）は IT9 持ち越し。</p>
 *
 * @param paymentDueDays         支払期限の日数（発行日 + N 日）デフォルト。Map 未設定 ShipperType に対する fallback
 * @param paymentDueDaysByType   ShipperType 別の支払期限日数（NET30 / NET60 / NET90 の設定駆動化）、null 可
 * @param overdue                督促スケジューラ設定
 * @param discountDescription    DISCOUNT 行の description テンプレート（{@code %d} で割引率を埋める）
 */
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        int paymentDueDays,
        Map<ShipperType, Integer> paymentDueDaysByType,
        Overdue overdue,
        String discountDescription
) {

    public BillingProperties {
        if (paymentDueDays <= 0) {
            throw new IllegalArgumentException("paymentDueDays は 1 以上である必要があります: " + paymentDueDays);
        }
        if (paymentDueDaysByType == null) {
            paymentDueDaysByType = Collections.emptyMap();
        } else {
            // 検証: 全値が 1 以上であること
            for (Map.Entry<ShipperType, Integer> e : paymentDueDaysByType.entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) {
                    throw new IllegalArgumentException(
                            "paymentDueDaysByType の値は 1 以上である必要があります: "
                                    + e.getKey() + "=" + e.getValue());
                }
            }
            // EnumMap で正規化（イテレーション順序の安定性 + アロケーション最適化）
            EnumMap<ShipperType, Integer> normalized = new EnumMap<>(ShipperType.class);
            normalized.putAll(paymentDueDaysByType);
            paymentDueDaysByType = Collections.unmodifiableMap(normalized);
        }
        if (overdue == null) {
            throw new IllegalArgumentException("overdue 設定は必須です");
        }
        if (discountDescription == null || discountDescription.isBlank()) {
            throw new IllegalArgumentException("discountDescription は必須です");
        }
    }

    /**
     * 指定 ShipperType の支払期限日数を返す。Map に未設定なら default の {@link #paymentDueDays()} を返す。
     */
    public int paymentDueDaysFor(ShipperType shipperType) {
        if (shipperType == null) {
            return paymentDueDays;
        }
        Integer days = paymentDueDaysByType.get(shipperType);
        return days != null ? days : paymentDueDays;
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
