package com.example.cargotracker.authms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserName 値オブジェクト")
class UserNameTest {

    @Test
    @DisplayName("有効なユーザー名で生成できる")
    void 有効なユーザー名で生成できる() {
        var name = new UserName("alice");
        assertThat(name.value()).isEqualTo("alice");
    }

    @Test
    @DisplayName("null は拒否する")
    void nullは拒否する() {
        assertThatThrownBy(() -> new UserName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空文字は拒否する")
    void 空文字は拒否する() {
        assertThatThrownBy(() -> new UserName(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("3 文字未満は拒否する")
    void 文字数が最小未満は拒否する() {
        assertThatThrownBy(() -> new UserName("ab"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("50 文字を超えると拒否する")
    void 文字数が最大超過は拒否する() {
        String tooLong = "a".repeat(51);
        assertThatThrownBy(() -> new UserName(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値の UserName は等しい")
    void 同じ値のUserNameは等しい() {
        assertThat(new UserName("alice")).isEqualTo(new UserName("alice"));
    }
}
