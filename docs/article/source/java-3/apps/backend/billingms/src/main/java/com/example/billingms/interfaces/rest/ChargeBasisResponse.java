package com.example.billingms.interfaces.rest;

import com.example.billingms.domain.model.valueobjects.TransportCharge;
import java.math.BigDecimal;

/**
 * 基本料金の根拠（[ADR-027] 決定 1）。
 *
 * <p><strong>4 つの係数をすべて返す。</strong>金額そのものより「なぜその金額か」が
 * 読めることを優先する——経理担当者は請求の根拠を荷主に説明する。
 *
 * <p><strong>距離は返さない</strong>——持っていない。区間と地域区分がその代わりである
 * （決定 1 の改訂）。<strong>区分も返す</strong>——「なぜ 1 区間で 30 万円なのか」は、
 * 遠洋であることを出さないと読めない。
 *
 * @param baseFare 基準運賃
 * @param legCount 区間数
 * @param legFactor 区間係数（区間ごとの地域係数の合計）
 * @param region 旅程で最も重い地域区分。運んでいなければ {@code null}
 * @param regionLabel 地域区分の表示名
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
        String region,
        String regionLabel,
        BigDecimal weightKg,
        BigDecimal weightFactor,
        String cargoType,
        String cargoTypeLabel,
        BigDecimal cargoTypeFactor) {

    public static ChargeBasisResponse from(TransportCharge charge) {
        return new ChargeBasisResponse(
                MoneyResponse.from(TransportCharge.BASE_FARE),
                charge.legCount(),
                charge.legFactor(),
                charge.region() == null ? null : charge.region().name(),
                charge.region() == null ? null : charge.region().label(),
                charge.weightKg(),
                charge.weightFactor(),
                charge.cargoType().name(),
                charge.cargoType().label(),
                charge.cargoTypeFactor());
    }
}
