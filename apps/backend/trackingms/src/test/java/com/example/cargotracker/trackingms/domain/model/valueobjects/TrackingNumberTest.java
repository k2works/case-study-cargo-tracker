package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackingNumber 値オブジェクト")
class TrackingNumberTest {

    @Test
    @DisplayName("bookingms 実装の TRK-YYYYMMDD-XXXXXXXX 形式を許容する")
    void canCreateWithBookingmsFormat() {
        TrackingNumber tn = new TrackingNumber("TRK-20260518-3053F0B8");
        assertThat(tn.value()).isEqualTo("TRK-20260518-3053F0B8");
    }

    @Test
    @DisplayName("シンプルな TRK- + 任意英数の形式も許容する")
    void canCreateWithSimpleFormat() {
        TrackingNumber tn = new TrackingNumber("TRK-ABC1234567");
        assertThat(tn.value()).isEqualTo("TRK-ABC1234567");
    }

    @Test
    @DisplayName("接頭辞 'TRK-' が無いと拒否する")
    void rejectMissingPrefix() {
        assertThatThrownBy(() -> new TrackingNumber("ABC1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-");
    }

    @Test
    @DisplayName("空文字を拒否する")
    void rejectBlank() {
        assertThatThrownBy(() -> new TrackingNumber(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空文字");
    }

    @Test
    @DisplayName("25 文字を超える値を拒否する")
    void rejectTooLong() {
        assertThatThrownBy(() -> new TrackingNumber("TRK-" + "A".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 文字");
    }

    @Test
    @DisplayName("null 値を拒否する")
    void rejectNull() {
        assertThatThrownBy(() -> new TrackingNumber(null))
                .isInstanceOf(NullPointerException.class);
    }
}
