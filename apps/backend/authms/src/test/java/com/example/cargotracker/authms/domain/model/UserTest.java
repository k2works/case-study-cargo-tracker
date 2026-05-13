package com.example.cargotracker.authms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User エンティティ")
class UserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 13, 10, 0, 0);

    private static User newUser() {
        return User.create(
                new UserName("alice"),
                new Email("alice@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
    }

    @Test
    @DisplayName("有効な値でユーザーを生成できる")
    void 有効な値でユーザーを生成できる() {
        var user = newUser();
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

    @Test
    @DisplayName("新規ユーザーは failedAttempts=0 でロックされていない")
    void 新規ユーザーはロックされていない() {
        var user = newUser();
        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockUntil()).isNull();
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("ログイン失敗を記録すると failedAttempts が 1 ずつ増える")
    void ログイン失敗で失敗カウンタが増える() {
        var user = newUser();
        user.recordFailedAttempt(NOW);
        assertThat(user.failedAttempts()).isEqualTo(1);
        user.recordFailedAttempt(NOW);
        assertThat(user.failedAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("5 回連続失敗で lockUntil が NOW + 30 分に設定される")
    void 五回失敗でロックされる() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }
        assertThat(user.failedAttempts()).isEqualTo(5);
        assertThat(user.lockUntil()).isEqualTo(NOW.plusMinutes(30));
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plusMinutes(29))).isTrue();
    }

    @Test
    @DisplayName("lockUntil 経過後は isLocked が false を返す（自動解除）")
    void 三十分経過でロック自動解除() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }
        assertThat(user.isLocked(NOW.plusMinutes(30))).isFalse();
        assertThat(user.isLocked(NOW.plusMinutes(31))).isFalse();
    }

    @Test
    @DisplayName("自動解除後の失敗は failedAttempts を 1 から数え直す")
    void 自動解除後の失敗はカウントリセットされる() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }
        // 30 分経過後にもう 1 度失敗
        var afterUnlock = NOW.plusMinutes(31);
        user.recordFailedAttempt(afterUnlock);
        assertThat(user.failedAttempts()).isEqualTo(1);
        assertThat(user.lockUntil()).isNull();
        assertThat(user.isLocked(afterUnlock)).isFalse();
    }

    @Test
    @DisplayName("ログイン成功で failedAttempts と lockUntil がリセットされる")
    void ログイン成功でリセットされる() {
        var user = newUser();
        user.recordFailedAttempt(NOW);
        user.recordFailedAttempt(NOW);
        user.recordSuccessfulLogin();
        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockUntil()).isNull();
    }

    @Test
    @DisplayName("ロック中であっても recordSuccessfulLogin で解除できる")
    void ロック中でも成功でリセットされる() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }
        assertThat(user.isLocked(NOW)).isTrue();
        user.recordSuccessfulLogin();
        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockUntil()).isNull();
        assertThat(user.isLocked(NOW)).isFalse();
    }
}
