package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

public final class DiscountRate {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("0.15");

    private final BigDecimal value;

    public DiscountRate(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("discountRate must not be null");
        }
        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("discountRate must be between 0.0 and 0.15");
        }
        this.value = value.stripTrailingZeros();
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiscountRate discountRate)) {
            return false;
        }
        return Objects.equals(value, discountRate.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
