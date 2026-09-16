package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscountRateTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0000", "0.1500", "0.3000"})
    @DisplayName("0.0000〜0.3000 は通す（境界を含む）")
    void acceptsInRange(String value) {
        assertThatCode(() -> new DiscountRate(new BigDecimal(value))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.0001", "0.3001", "1.0000"})
    @DisplayName("範囲の外は受け付けない")
    void rejectsOutOfRange(String value) {
        assertThatThrownBy(() -> new DiscountRate(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0000〜0.3000");
    }

    @Test
    @DisplayName("null は受け付けない")
    void rejectsNull() {
        assertThatThrownBy(() -> new DiscountRate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
