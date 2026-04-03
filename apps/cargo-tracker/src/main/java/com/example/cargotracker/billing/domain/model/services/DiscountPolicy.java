package com.example.cargotracker.billing.domain.model.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 法人割引方針ドメインサービス。
 * 割引率に基づいて割引額を計算する。
 */
public class DiscountPolicy {

    private static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("30");
    private static final BigDecimal RATE_DIVISOR = new BigDecimal("100");

    /**
     * 割引額を計算する。
     * 割引額 = baseAmount × (discountRate / 100)
     * 四捨五入（HALF_UP）でスケール 2 に丸める。
     *
     * @param baseAmount   基本料金（null 不可）
     * @param discountRate 割引率（0〜30、null 不可）
     * @return マイナス符号付きの割引額（adjustmentAmount として applyAdjustment() に渡す）
     */
    public BigDecimal calculateDiscount(BigDecimal baseAmount, BigDecimal discountRate) {
        if (baseAmount == null) throw new IllegalArgumentException("基本料金は null にできません");
        if (discountRate == null) throw new IllegalArgumentException("割引率は null にできません");
        if (discountRate.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("割引率は 0 以上でなければなりません");
        if (discountRate.compareTo(MAX_DISCOUNT_RATE) > 0)
            throw new IllegalArgumentException("割引率は 30 以下でなければなりません");

        BigDecimal discount = baseAmount
                .multiply(discountRate)
                .divide(RATE_DIVISOR, 2, RoundingMode.HALF_UP);
        return discount.negate();
    }
}
