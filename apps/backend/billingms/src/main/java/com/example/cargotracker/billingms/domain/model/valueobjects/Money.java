package com.example.cargotracker.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * 通貨金額を表す値オブジェクト。
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency JPY = Currency.getInstance("JPY");

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金額は 0 以上である必要があります: " + amount);
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money ofJpy(long amount) {
        return new Money(BigDecimal.valueOf(amount), JPY);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor).setScale(0, RoundingMode.HALF_UP), currency);
    }

    public Money subtract(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("通貨が一致しません");
        }
        var result = amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("差し引き後の金額が負になります");
        }
        return new Money(result, currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
