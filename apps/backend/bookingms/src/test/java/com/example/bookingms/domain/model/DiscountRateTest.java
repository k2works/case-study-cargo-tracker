package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("割引率")
class DiscountRateTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.1", "12.5", "29.9", "30"})
    @DisplayName("0〜30% は受け付ける（境界を含む）")
    void acceptsWithinRange(String percent) {
        assertThat(DiscountRate.ofPercent(new BigDecimal(percent)).percent())
                .isEqualByComparingTo(new BigDecimal(percent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "-1", "30.1", "100"})
    @DisplayName("範囲の外は受け付けない")
    void rejectsOutOfRange(String percent) {
        assertThatThrownBy(() -> DiscountRate.ofPercent(new BigDecimal(percent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("割引率は 0〜30");
    }

    @Test
    @DisplayName("null は受け付けない")
    void rejectsNull() {
        assertThatThrownBy(() -> DiscountRate.ofPercent(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("永続化の割合（0.1250）と百分率（12.5%）を相互に変換する")
    void convertsBetweenRateAndPercent() {
        // DB は割合、画面は百分率で扱う。変換を各所に書くと片方だけ 100 倍される
        DiscountRate rate = DiscountRate.ofPercent(new BigDecimal("12.5"));

        assertThat(rate.rate()).isEqualByComparingTo(new BigDecimal("0.1250"));
        assertThat(DiscountRate.ofRate(new BigDecimal("0.1250")).percent())
                .isEqualByComparingTo(new BigDecimal("12.5"));
    }

    @Test
    @DisplayName("同じ率は等しい")
    void equality() {
        assertThat(DiscountRate.ofPercent(new BigDecimal("10")))
                .isEqualTo(DiscountRate.ofPercent(new BigDecimal("10.00")));
        assertThat(DiscountRate.ofPercent(new BigDecimal("10")))
                .hasSameHashCodeAs(DiscountRate.ofPercent(new BigDecimal("10.00")));
        assertThat(DiscountRate.ofPercent(new BigDecimal("10")))
                .isNotEqualTo(DiscountRate.ofPercent(new BigDecimal("20")));
        assertThat(DiscountRate.ofPercent(new BigDecimal("10"))).isNotEqualTo("10");
    }

    @Test
    @DisplayName("表示は百分率")
    void toStringIsPercent() {
        assertThat(DiscountRate.ofPercent(new BigDecimal("12.5"))).hasToString("12.5%");
    }
}
