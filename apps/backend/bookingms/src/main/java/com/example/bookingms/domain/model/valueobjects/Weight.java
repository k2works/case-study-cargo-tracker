package com.example.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 重量値オブジェクト（kg 単位）
 */
public class Weight {

    private final BigDecimal kg;

    public Weight(BigDecimal kg) {
        Objects.requireNonNull(kg, "weight must not be null");
        if (kg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        this.kg = kg;
    }

    public BigDecimal getKg() {
        return kg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Weight weight = (Weight) o;
        return Objects.equals(kg, weight.kg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kg);
    }

    @Override
    public String toString() {
        return kg + " kg";
    }
}
