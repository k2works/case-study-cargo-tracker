package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 追跡番号（trackingms の型）。
 *
 * <p><b>採番しない。</b> 番号は bookingms の投影が採り、契約コマンドで届く。ここが
 * 見るのは「届いた値が使えるか」だけである。</p>
 */
class TrackingNumberTest {

    @Test
    @DisplayName("前後の空白は落とす（表示と照合で食い違わない）")
    void trimsSurroundingSpaces() {
        assertThat(TrackingNumber.of("  T-2026-000001  ").value())
                .isEqualTo("T-2026-000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("空の追跡番号は断る（誰の荷物か辿れない追跡ができる）")
    void rejectsBlank(String value) {
        assertThatThrownBy(() -> TrackingNumber.of(value))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("null の追跡番号は断る")
    void rejectsNull() {
        assertThatThrownBy(() -> TrackingNumber.of(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
