package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * 荷主契約値オブジェクト（US22 / IT7 タスク 3.1）。
 *
 * <p>{@link CorporateDiscountPolicy#apply(BigDecimal, CorporateContract)} に渡され、
 * 法人荷主の場合に契約割引率（0〜0.30）を基本料金に適用する。</p>
 *
 * <p>不変条件:</p>
 *
 * <ul>
 *   <li>{@code shipperId} は非 blank</li>
 *   <li>{@code shipperType} は非 null</li>
 *   <li>{@code discountRate} は 0.00 ≤ rate ≤ 0.30（domain-model.md L946-949 / iteration_plan-7）</li>
 *   <li>{@link ShipperType#INDIVIDUAL} のときは {@code discountRate == 0} 強制</li>
 * </ul>
 *
 * @param shipperId    荷主識別子（bookingms と同一）
 * @param shipperType  荷主種別（CORPORATE / INDIVIDUAL）
 * @param discountRate 契約割引率（0〜0.30、INDIVIDUAL は 0）
 */
public record CorporateContract(
        String shipperId,
        ShipperType shipperType,
        BigDecimal discountRate
) {

    private static final BigDecimal MAX_RATE = new BigDecimal("0.30");

    public CorporateContract {
        if (shipperId == null || shipperId.isBlank()) {
            throw new IllegalArgumentException("shipperId は必須です");
        }
        if (shipperType == null) {
            throw new IllegalArgumentException("shipperType は必須です");
        }
        if (discountRate == null) {
            throw new IllegalArgumentException("discountRate は必須です");
        }
        if (discountRate.signum() < 0 || discountRate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException(
                    "discountRate は 0.00〜0.30 の範囲で必須です: " + discountRate);
        }
        if (shipperType == ShipperType.INDIVIDUAL && discountRate.signum() != 0) {
            throw new IllegalArgumentException(
                    "INDIVIDUAL 荷主の discountRate は 0 でなければなりません: " + discountRate);
        }
    }
}
