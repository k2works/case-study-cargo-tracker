package com.example.cargotracker.billingms.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 法人契約値オブジェクト（US22）。
 *
 * @param shipperId              荷主 ID
 * @param shipperType            荷主種別（CORPORATE / INDIVIDUAL）
 * @param contractedDiscountRate 契約割引率（0.00〜0.30）
 */
public record CorporateContract(
        String shipperId,
        ShipperType shipperType,
        BigDecimal contractedDiscountRate) {

    private static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("0.30");

    public CorporateContract {
        if (contractedDiscountRate.compareTo(MAX_DISCOUNT_RATE) > 0) {
            throw new IllegalArgumentException(
                    "割引率は 30% 以下である必要があります: " + contractedDiscountRate);
        }
        if (contractedDiscountRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "割引率は 0 以上である必要があります: " + contractedDiscountRate);
        }
    }
}
