package com.example.cargotracker.billing.domain.model.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DiscountPolicy ドメインサービス")
class DiscountPolicyTest {

    private final DiscountPolicy policy = new DiscountPolicy();

    // ── 正常系 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("割引率0の場合は割引額0を返す")
    void calculateDiscount_zeroRate_returnsZero() {
        BigDecimal result = policy.calculateDiscount(new BigDecimal("1000"), BigDecimal.ZERO);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("割引率10%の場合は基本料金の10%をマイナス値で返す")
    void calculateDiscount_rate10_returnsTenPercent() {
        BigDecimal result = policy.calculateDiscount(new BigDecimal("1000"), new BigDecimal("10"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    @DisplayName("割引率30%の場合は基本料金の30%をマイナス値で返す")
    void calculateDiscount_rate30_returnsThirtyPercent() {
        BigDecimal result = policy.calculateDiscount(new BigDecimal("1000"), new BigDecimal("30"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-300.00"));
    }

    @Test
    @DisplayName("割引額は小数点以下をHALF_UPで四捨五入する")
    void calculateDiscount_roundsHalfUp() {
        // 100.05 * 10 / 100 = 10.005 → HALF_UP scale 2 → 10.01
        BigDecimal result = policy.calculateDiscount(new BigDecimal("100.05"), new BigDecimal("10"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-10.01"));
    }

    // ── バリデーション ────────────────────────────────────────────────────

    @Test
    @DisplayName("基本料金がnullの場合はIllegalArgumentExceptionをスローする")
    void calculateDiscount_nullBaseAmount_throwsException() {
        assertThatThrownBy(() -> policy.calculateDiscount(null, new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("割引率がnullの場合はIllegalArgumentExceptionをスローする")
    void calculateDiscount_nullDiscountRate_throwsException() {
        assertThatThrownBy(() -> policy.calculateDiscount(new BigDecimal("1000"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("割引率が負の場合はIllegalArgumentExceptionをスローする")
    void calculateDiscount_negativeRate_throwsException() {
        assertThatThrownBy(() -> policy.calculateDiscount(new BigDecimal("1000"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("割引率が30超の場合はIllegalArgumentExceptionをスローする")
    void calculateDiscount_rateExceeds30_throwsException() {
        assertThatThrownBy(() -> policy.calculateDiscount(new BigDecimal("1000"), new BigDecimal("31")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
