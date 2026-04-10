package com.example.cargotracker.billing.domain.model;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DiscountPolicyTest {

    @Test
    void nonePolicy_shouldReturnBaseAmount() {
        DiscountPolicy policy = DiscountPolicy.none();

        assertEquals(DiscountType.NONE, policy.type());
        assertEquals(BigDecimal.ZERO, policy.rate());
        assertEquals(500_000, policy.apply(500_000));
    }

    @Test
    void corporatePolicy_shouldApplyDiscount() {
        DiscountPolicy policy = DiscountPolicy.corporate(new BigDecimal("0.10"));

        assertEquals(DiscountType.CORPORATE, policy.type());
        assertEquals(450_000, policy.apply(500_000));
    }

    @Test
    void corporatePolicy_withMaxRate_shouldApplyMaxDiscount() {
        DiscountPolicy policy = DiscountPolicy.corporate(new BigDecimal("0.15"));

        assertEquals(425_000, policy.apply(500_000));
    }

    @Test
    void apply_shouldTruncateDecimal() {
        DiscountPolicy policy = DiscountPolicy.corporate(new BigDecimal("0.10"));

        assertEquals(9, policy.apply(10));
    }

    @Test
    void shouldThrow_whenTypeIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscountPolicy(null, BigDecimal.ZERO));
    }

    @Test
    void shouldThrow_whenRateIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscountPolicy(DiscountType.NONE, null));
    }

    @Test
    void shouldThrow_whenRateIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscountPolicy(DiscountType.CORPORATE, new BigDecimal("-0.01")));
    }

    @Test
    void shouldThrow_whenRateExceedsOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscountPolicy(DiscountType.CORPORATE, new BigDecimal("1.01")));
    }
}
