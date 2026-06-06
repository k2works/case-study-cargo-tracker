package com.example.billingms.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BalanceTracker} の単体テスト（IT9 A1.3 / ADR-0020 / US26）。
 */
class BalanceTrackerTest {

    @Test
    void 初期状態は累積入金額がゼロで総額と一致する() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"));

        assertThat(tracker.totalDue()).isEqualByComparingTo("100000");
        assertThat(tracker.paidSoFar()).isEqualByComparingTo("0");
        assertThat(tracker.remainingBalance()).isEqualByComparingTo("100000");
        assertThat(tracker.isFullyPaid()).isFalse();
    }

    @Test
    void 部分入金を適用すると累積入金額が増加し残額が減る() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"))
                .apply(new BigDecimal("30000"));

        assertThat(tracker.paidSoFar()).isEqualByComparingTo("30000");
        assertThat(tracker.remainingBalance()).isEqualByComparingTo("70000");
        assertThat(tracker.isFullyPaid()).isFalse();
    }

    @Test
    void 複数回の部分入金を適用すると累積される() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"))
                .apply(new BigDecimal("30000"))
                .apply(new BigDecimal("40000"));

        assertThat(tracker.paidSoFar()).isEqualByComparingTo("70000");
        assertThat(tracker.remainingBalance()).isEqualByComparingTo("30000");
    }

    @Test
    void 残額と一致する入金を適用すると完全入金となる() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"))
                .apply(new BigDecimal("60000"))
                .apply(new BigDecimal("40000"));

        assertThat(tracker.paidSoFar()).isEqualByComparingTo("100000");
        assertThat(tracker.remainingBalance()).isEqualByComparingTo("0");
        assertThat(tracker.isFullyPaid()).isTrue();
    }

    @Test
    void 総額を超える入金を適用すると例外を投げる() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"))
                .apply(new BigDecimal("70000"));

        assertThatThrownBy(() -> tracker.apply(new BigDecimal("40000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("総額を超過");
    }

    @Test
    void 負の金額の入金は例外を投げる() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"));

        assertThatThrownBy(() -> tracker.apply(new BigDecimal("-1000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 より大きい");
    }

    @Test
    void ゼロ金額の入金は例外を投げる() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"));

        assertThatThrownBy(() -> tracker.apply(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 より大きい");
    }

    @Test
    void 総額の更新により残額が再計算される() {
        BalanceTracker tracker = BalanceTracker.of(new BigDecimal("100000"))
                .apply(new BigDecimal("30000"))
                .withTotalDue(new BigDecimal("80000"));

        assertThat(tracker.totalDue()).isEqualByComparingTo("80000");
        assertThat(tracker.paidSoFar()).isEqualByComparingTo("30000");
        assertThat(tracker.remainingBalance()).isEqualByComparingTo("50000");
    }
}
