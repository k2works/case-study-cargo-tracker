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
    @ValueSource(strings = {
        "", "   ",
        "example.com",          // @ が無い
        "a@",                   // ドメインが無い
        "@example.com",         // ローカル部が無い
        "a@b",                  // ドメインに「.」が無い
        "a b@example.com",      // ローカル部に空白
        "a@example .com",       // ドメインに空白
        "a@b@example.com",      // @ が 2 つ
        "a@.example.com",       // ドメインが「.」で始まる
        "a@example.com.",       // ドメインが「.」で終わる
        "a@example..com",       // 空のラベル
    })
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
    @DisplayName("「.」が続く長い入力でもスタックを食い潰さない")
    void survivesLongRepetitiveInput() {
        // 入れ子の量指定子を使うと、この形の入力で総当たりに落ちる。
        String domain = "a." .repeat(120) + "example.com";
        String candidate = "user@" + domain;

        assertThatCode(() -> {
            try {
                new Email(candidate);
            } catch (IllegalArgumentException expected) {
                // 形として弾かれるのは構わない。落ちないことが要件。
            }
        }).doesNotThrowAnyException();
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
