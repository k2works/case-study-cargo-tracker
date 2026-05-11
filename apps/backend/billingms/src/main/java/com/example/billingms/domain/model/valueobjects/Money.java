package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金額値オブジェクト（不変）
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        amount = amount.setScale(0, RoundingMode.HALF_UP);
    }

    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money ofJpy(long amount) {
        return of(amount, "JPY");
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add Money with different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public long toLong() {
        return amount.longValue();
    }
}
