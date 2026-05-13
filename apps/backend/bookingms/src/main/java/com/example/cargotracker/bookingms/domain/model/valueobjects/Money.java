package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 通貨と金額のペア。
 *
 * <p>data-model.md「{@code NUMERIC(14,2)} + ISO 4217 の {@code VARCHAR(3)} 通貨カラムのペア」に準拠。</p>
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money の金額は非負である必要があります: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency は ISO 4217 の 3 文字コードである必要があります: " + currency);
        }
    }
}
