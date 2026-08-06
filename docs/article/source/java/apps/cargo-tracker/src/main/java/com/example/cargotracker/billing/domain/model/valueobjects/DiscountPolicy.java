package com.example.cargotracker.billing.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record DiscountPolicy(DiscountType type, BigDecimal rate) {

    public DiscountPolicy {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (rate == null) {
            throw new IllegalArgumentException("rate must not be null");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate must be between 0 and 1");
        }
    }

    public static DiscountPolicy none() {
        return new DiscountPolicy(DiscountType.NONE, BigDecimal.ZERO);
    }

    public static DiscountPolicy corporate(BigDecimal rate) {
        return new DiscountPolicy(DiscountType.CORPORATE, rate);
    }

    public int apply(int baseAmount) {
        if (type == DiscountType.NONE || rate.compareTo(BigDecimal.ZERO) == 0) {
            return baseAmount;
        }
        BigDecimal discount = BigDecimal.valueOf(baseAmount).multiply(rate).setScale(0, RoundingMode.DOWN);
        return baseAmount - discount.intValue();
    }
}
