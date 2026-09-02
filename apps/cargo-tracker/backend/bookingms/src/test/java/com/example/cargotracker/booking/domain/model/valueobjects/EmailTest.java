package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @ParameterizedTest
    @ValueSource(strings = {"a@example.com", "yamada.taro@sub.example.co.jp", "a+b@example.com"})
    @DisplayName("正しい形は通す")
    void acceptsValidShapes(String value) {
        assertThatCode(() -> new Email(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "example.com", "a@", "@example.com", "a@b", "a b@example.com",
            "a@example .com"})
    @DisplayName("形が違えば受け付けない")
    void rejectsInvalidShapes(String value) {
        assertThatThrownBy(() -> new Email(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null は受け付けない")
    void rejectsNull() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必須");
    }

    @Test
    @DisplayName("長すぎるものは受け付けない")
    void rejectsTooLong() {
        String local = "a".repeat(250);
        assertThatThrownBy(() -> new Email(local + "@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("長すぎます");
    }
}
