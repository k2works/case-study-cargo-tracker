package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackingNumber 値オブジェクト")
class TrackingNumberTest {

    @Test
    @DisplayName("正しい書式（TRK- + 大文字英数 10 桁）で生成できる")
    void canCreateWithValidFormat() {
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
    @DisplayName("英数 10 桁未満は拒否する")
    void rejectTooShort() {
        assertThatThrownBy(() -> new TrackingNumber("TRK-ABC123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("小文字を含む場合は拒否する")
    void rejectLowercase() {
        assertThatThrownBy(() -> new TrackingNumber("TRK-abc1234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 値を拒否する")
    void rejectNull() {
        assertThatThrownBy(() -> new TrackingNumber(null))
                .isInstanceOf(NullPointerException.class);
    }
}
