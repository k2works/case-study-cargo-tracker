package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.math.BigDecimal;

public record DiscountRate(BigDecimal value) {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("0.30");

    public DiscountRate {
        if (value == null) {
            throw new IllegalArgumentException("discountRate must not be null");
        }
        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("discountRate must be between 0.0 and 0.30");
        }
        value = value.stripTrailingZeros();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
