package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;

/** 法人契約の割引率。0.0000〜0.3000（domain-model.md）。 */
public record DiscountRate(BigDecimal value) {

    private static final BigDecimal MIN = new BigDecimal("0.0000");
    private static final BigDecimal MAX = new BigDecimal("0.3000");

    public DiscountRate {
        if (value == null) {
            throw new BusinessRuleViolation("割引率は必須です");
        }
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new BusinessRuleViolation("割引率は 0.0000〜0.3000 の範囲です: " + value);
        }
    }
}
