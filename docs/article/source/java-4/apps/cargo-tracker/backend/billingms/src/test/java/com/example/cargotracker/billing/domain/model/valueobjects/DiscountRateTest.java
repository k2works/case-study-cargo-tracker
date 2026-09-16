package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 割引率（US22 §受入基準 2）。
 *
 * <p><b>範囲は値オブジェクトが守る。</b> bookingms にも同名の型があるが、
 * <b>BC が違えば型も違う</b>（共有カーネルには置かない）。</p>
 */
class DiscountRateTest {

    @Test
    @DisplayName("US22 §2: 0〜30% の範囲（境界は両側から見る）")
    void staysWithinZeroToThirtyPercent() {
        assertThat(DiscountRate.of(new BigDecimal("0.0000")).percentage()).isEqualByComparingTo("0");
        assertThat(DiscountRate.of(new BigDecimal("0.3000")).percentage())
                .isEqualByComparingTo("30");

        assertThatThrownBy(() -> DiscountRate.of(new BigDecimal("0.3001")))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("30");
        assertThatThrownBy(() -> DiscountRate.of(new BigDecimal("-0.0001")))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("割引の無い荷主は 0%（null も 0% として扱う）")
    void noneMeansZero() {
        // 個人荷主は契約を持たないので、スナップショットの割引率は NULL になる。
        // **そこで落とさない**——0% として扱うのが業務上正しい。
        assertThat(DiscountRate.ofNullable(null)).isEqualTo(DiscountRate.none());
        assertThat(DiscountRate.none().isZero()).isTrue();
    }

    @Test
    @DisplayName("基本料金に当てた割引額を返す（丸めは Money の中）")
    void computesTheDiscountAmount() {
        Money base = Money.yen(new BigDecimal("1190000"));

        assertThat(DiscountRate.of(new BigDecimal("0.1500")).appliedTo(base).roundToUnit().amount())
                .isEqualByComparingTo("178500");
        assertThat(DiscountRate.none().appliedTo(base).isZero()).isTrue();
    }

    @Test
    @DisplayName("率そのものも読める（明細に出す百分率）")
    void exposesItsValue() {
        assertThat(DiscountRate.of(new BigDecimal("0.1500")).value())
                .isEqualByComparingTo("0.1500");
        assertThat(DiscountRate.of(new BigDecimal("0.1500")).isZero()).isFalse();
    }
}
