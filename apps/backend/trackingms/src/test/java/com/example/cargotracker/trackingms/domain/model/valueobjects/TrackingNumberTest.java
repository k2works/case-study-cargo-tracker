package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackingNumber 値オブジェクト")
class TrackingNumberTest {

    @Test
    @DisplayName("TRK-YYYYMMDD-XXXXXXXX 形式を受け入れる")
    void canCreateWithBookingmsFormat() {
        TrackingNumber tn = new TrackingNumber("TRK-20260518-3053F0B8");
        assertThat(tn.value()).isEqualTo("TRK-20260518-3053F0B8");
    }

    @Test
    @DisplayName("旧書式 TRK-大文字英数10桁は拒否する")
    void rejectOldFormat() {
        assertThatThrownBy(() -> new TrackingNumber("TRK-ABC1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-YYYYMMDD-XXXXXXXX");
    }

    @Test
    @DisplayName("接頭辞 'TRK-' が無いと拒否する")
    void rejectMissingPrefix() {
        assertThatThrownBy(() -> new TrackingNumber("ABC1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-YYYYMMDD-XXXXXXXX");
    }

    @Test
    @DisplayName("null 値を拒否する")
    void rejectNull() {
        assertThatThrownBy(() -> new TrackingNumber(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("小文字を含むと拒否する")
    void rejectLowercase() {
        assertThatThrownBy(() -> new TrackingNumber("TRK-20260518-3053f0b8"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
