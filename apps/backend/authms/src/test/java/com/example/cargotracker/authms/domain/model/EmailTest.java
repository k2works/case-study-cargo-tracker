package com.example.cargotracker.authms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email 値オブジェクト")
class EmailTest {

    @Test
    @DisplayName("有効なメールアドレスで生成できる")
    void 有効なメールアドレスで生成できる() {
        var email = new Email("user@example.com");
        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("null は拒否する")
    void nullは拒否する() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("形式が不正なメールアドレスは拒否する")
    void 形式が不正なメールアドレスは拒否する() {
        assertThatThrownBy(() -> new Email("invalid-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値のEmailは等しい")
    void 同じ値のEmailは等しい() {
        assertThat(new Email("a@b.com")).isEqualTo(new Email("a@b.com"));
    }
}
