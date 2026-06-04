package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * 輸送実績（US21、domain-model.md L927-933、iteration_plan-7 §設計）。
 *
 * <p>{@code FareCalculator}（Task 2.2）の入力となる値オブジェクト。経路距離・貨物重量・
 * 貨物種別・荷役回数・通貨を保持し、コンストラクタで不変条件を検証する。</p>
 *
 * @param distanceKm     経路の累計距離（km、0 以上、null 不可）
 * @param weightKg       貨物重量（kg、0 より大、null 不可）
 * @param cargoType      貨物種別（GENERAL / HAZARDOUS / REFRIGERATED、null/空白不可）
 * @param handlingCount  荷役作業回数（0 以上、HandlingActivityAcl で集計、Task 2.4）
 * @param currency       通貨コード（ISO 4217 3 文字、null/空白不可、Invoice 集約内で一貫性必須）
 */
public record TransportRecord(
        BigDecimal distanceKm,
        BigDecimal weightKg,
        String cargoType,
        int handlingCount,
        String currency
) {

    public TransportRecord {
        if (distanceKm == null || distanceKm.signum() < 0) {
            throw new IllegalArgumentException("distanceKm は 0 以上の値で必須です: " + distanceKm);
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg は 0 より大きい値で必須です: " + weightKg);
        }
        if (cargoType == null || cargoType.isBlank()) {
            throw new IllegalArgumentException("cargoType は必須です");
        }
        if (handlingCount < 0) {
            throw new IllegalArgumentException("handlingCount は 0 以上で必須です: " + handlingCount);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency は必須です");
        }
    }
}
