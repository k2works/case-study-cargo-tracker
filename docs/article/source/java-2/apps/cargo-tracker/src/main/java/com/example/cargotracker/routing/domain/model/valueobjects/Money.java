package com.example.cargotracker.routing.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金額。Routing Context が扱う概算費用の型（ADR-008）。
 *
 * <p><strong>通貨を必ず伴う。</strong> 単位の無い金額は金額ではない。
 *
 * @param value    金額
 * @param currency 通貨コード（ISO 4217）
 */
public record Money(BigDecimal value, String currency) {

    /** 本システムの既定通貨。 */
    public static final String JPY = "JPY";

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("金額は必須です");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("金額は負にできません: " + value);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("通貨は必須です");
        }
        // 最小通貨単位で保持する（data-model.md の estimated_cost_value は INTEGER）
        value = value.setScale(0, RoundingMode.HALF_UP);
        currency = currency.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public static Money yen(BigDecimal value) {
        return new Money(value, JPY);
    }
}
