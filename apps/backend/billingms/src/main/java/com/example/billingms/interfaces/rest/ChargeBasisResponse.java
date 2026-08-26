package com.example.billingms.interfaces.rest;

import com.example.billingms.domain.model.TransportCharge;
import java.math.BigDecimal;

/**
 * 基本料金の根拠（[ADR-027] 決定 1）。
 *
 * <p><strong>4 つの係数をすべて返す。</strong>金額そのものより「なぜその金額か」が
 * 読めることを優先する——経理担当者は請求の根拠を荷主に説明する。
 *
 * <p><strong>距離は返さない</strong>——持っていない。区間数がその代わりである。
 *
 * @param baseFare 基準運賃
 * @param legCount 区間数
 * @param legFactor 区間係数
 * @param weightKg 重量
 * @param weightFactor 重量係数
 * @param cargoType 貨物種別
 * @param cargoTypeLabel 貨物種別の表示名
 * @param cargoTypeFactor 貨物種別係数
 */
public record ChargeBasisResponse(
        MoneyResponse baseFare,
        int legCount,
        BigDecimal legFactor,
        BigDecimal weightKg,
        BigDecimal weightFactor,
        String cargoType,
        String cargoTypeLabel,
        BigDecimal cargoTypeFactor) {

    private static final java.util.Map<String, String> LABELS = java.util.Map.of(
            "GENERAL", "一般貨物",
            "HAZARDOUS", "危険物",
            "REFRIGERATED", "冷凍・冷蔵貨物");

    public static ChargeBasisResponse from(TransportCharge charge) {
        return new ChargeBasisResponse(
                MoneyResponse.from(TransportCharge.BASE_FARE),
                charge.legCount(),
                charge.legFactor(),
                charge.weightKg(),
                charge.weightFactor(),
                charge.cargoType().name(),
                LABELS.getOrDefault(charge.cargoType().name(), charge.cargoType().name()),
                charge.cargoTypeFactor());
    }
}
