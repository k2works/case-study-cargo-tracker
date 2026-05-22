package com.example.authms.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void ユーザーが生成できる() {
        User user = new User(1L, "shipper01", "hashed", Role.ROLE_SHIPPER, 0, null);
        assertThat(user.getUsername()).isEqualTo("shipper01");
        assertThat(user.getRole()).isEqualTo(Role.ROLE_SHIPPER);
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void ログイン失敗5回でアカウントがロックされる() {
        User user = new User(1L, "user01", "hashed", Role.ROLE_SHIPPER, 0, null);

        for (int i = 0; i < 4; i++) {
            user.incrementFailedAttempts();
            assertThat(user.isLocked()).isFalse();
        }

        user.incrementFailedAttempts();
        assertThat(user.isLocked()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedAt()).isNotNull();
    }

    @Test
    void 失敗回数をリセットするとロックが解除される() {
        User user = new User(1L, "user01", "hashed", Role.ROLE_SHIPPER, 0, null);
        for (int i = 0; i < 5; i++) {
            user.incrementFailedAttempts();
        }
        assertThat(user.isLocked()).isTrue();

        user.resetFailedAttempts();
        assertThat(user.isLocked()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void 全ロールが定義されている() {
        assertThat(Role.values()).containsExactlyInAnyOrder(
                Role.ROLE_SHIPPER, Role.ROLE_CONSIGNEE, Role.ROLE_SALES,
                Role.ROLE_ROUTING, Role.ROLE_TRACKER, Role.ROLE_HANDLER,
                Role.ROLE_ACCOUNTANT, Role.ROLE_ADMIN);
    }
}
