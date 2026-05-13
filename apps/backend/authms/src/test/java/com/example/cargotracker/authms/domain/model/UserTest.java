package com.example.cargotracker.authms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User エンティティ")
class UserTest {

    @Test
    @DisplayName("有効な値でユーザーを生成できる")
    void 有効な値でユーザーを生成できる() {
        var user = User.create(
                new UserName("alice"),
                new Email("alice@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        assertThat(user.username().value()).isEqualTo("alice");
        assertThat(user.email().value()).isEqualTo("alice@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.id()).isNotNull();
    }

    @Test
    @DisplayName("username が null は拒否する")
    void usernameがnullは拒否する() {
        var email = new Email("alice@example.com");
        var hash = new PasswordHash("$2a$10$hashed");
        assertThatThrownBy(() -> User.create(null, email, hash))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
