package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("メールアドレス")
class EmailAddressTest {

    @Test
    @DisplayName("形式が整っていれば受け入れる")
    void accepts() {
        assertThat(EmailAddress.of("yamada@example.com").value()).isEqualTo("yamada@example.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "yamada", "yamada@", "@example.com", "yamada@example",
        "ya mada@example.com", "yamada@exa mple.com"})
    @DisplayName("連絡にも重複判定にも使えない値は、理由を添えて拒む")
    void rejects(String value) {
        assertThatThrownBy(() -> EmailAddress.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("メールアドレスの形式が不正です");
    }

    @Test
    @DisplayName("復元では検査しない（規則が無かったころの行が読めなくなるため）")
    void restoreDoesNotValidate() {
        assertThat(EmailAddress.restore("yamada").value()).isEqualTo("yamada");
    }

    @Test
    @DisplayName("同じ文字列なら同じ値として扱う")
    void equality() {
        assertThat(EmailAddress.of("a@example.com")).isEqualTo(EmailAddress.of("a@example.com"));
    }
}
