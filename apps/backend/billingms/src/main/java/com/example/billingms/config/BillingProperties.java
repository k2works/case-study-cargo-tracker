package com.example.billingms.config;

import com.example.billingms.domain.model.ShipperType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        String discountDescription,
        RateTableSettings rateTable
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
        if (rateTable == null) {
            // IT8 T1.8: 未指定時は default テーブル（S20 UI サンプル値）を使用
            rateTable = RateTableSettings.defaultSettings();
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
     * 料金単価表設定（IT8 T1.8、運用設定駆動化）。
     *
     * <p>従来 {@code RateTable.defaultTable()} のコード内定数を application.yml に逃がし、
     * 経理担当者が設定変更（料金改定）→ アプリ再起動で反映可能にする。完全な DB 駆動化
     * （管理 UI 経由でランタイム反映）は IT9 持ち越し。</p>
     *
     * @param rates           貨物種別 → 単価係数（円/kg/km）
     * @param handlingUnitFee 1 回あたり取扱費（円、0 以上）
     */
    public record RateTableSettings(
            Map<String, BigDecimal> rates,
            BigDecimal handlingUnitFee
    ) {
        public RateTableSettings {
            if (rates == null || rates.isEmpty()) {
                throw new IllegalArgumentException("rates は 1 件以上の単価エントリが必須です");
            }
            if (handlingUnitFee == null || handlingUnitFee.signum() < 0) {
                throw new IllegalArgumentException(
                        "handlingUnitFee は 0 以上の値で必須です: " + handlingUnitFee);
            }
            // 順序保持コピー
            rates = Collections.unmodifiableMap(new LinkedHashMap<>(rates));
        }

        /** S20 UI サンプル値と整合する default 設定。 */
        public static RateTableSettings defaultSettings() {
            Map<String, BigDecimal> defaults = new LinkedHashMap<>();
            defaults.put("GENERAL", new BigDecimal("0.05"));
            defaults.put("HAZARDOUS", new BigDecimal("0.08"));
            defaults.put("REFRIGERATED", new BigDecimal("0.10"));
            return new RateTableSettings(defaults, new BigDecimal("1500"));
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
