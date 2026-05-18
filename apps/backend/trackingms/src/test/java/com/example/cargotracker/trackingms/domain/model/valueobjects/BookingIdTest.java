package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BookingId 値オブジェクト")
class BookingIdTest {

    @Test
    @DisplayName("UUID 文字列で生成できる")
    void canCreateWithUuid() {
        BookingId id = new BookingId("550e8400-e29b-41d4-a716-446655440000");
        assertThat(id.value()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("空文字を拒否する")
    void rejectBlank() {
        assertThatThrownBy(() -> new BookingId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null を拒否する")
    void rejectNull() {
        assertThatThrownBy(() -> new BookingId(null))
                .isInstanceOf(NullPointerException.class);
    }
}
