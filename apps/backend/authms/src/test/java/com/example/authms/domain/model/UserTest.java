package com.example.authms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("利用者")
class UserTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");

    private User active() {
        return User.restore(
                1L, "sales01", "sales@example.com", "テスト利用者", "hashed", true, 0, null, Set.of(Role.ROLE_SALES));
    }

    @Nested
    @DisplayName("ログインできる状態か")
    class Availability {

        @Test
        @DisplayName("有効かつ未ロックなら受け付ける")
        void acceptsActiveUser() {
            assertThat(active().canAttemptLoginAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("無効化されていれば受け付けない")
        void rejectsDisabledUser() {
            User disabled = User.restore(
                1L, "sales01", "s@example.com", "テスト利用者", "hashed", false, 0, null, Set.of(Role.ROLE_SALES));

            assertThat(disabled.canAttemptLoginAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("ロック期限が先ならまだ受け付けない")
        void rejectsWhileLocked() {
            User locked = User.restore(
                1L, "s", "s@example.com", "テスト利用者", "h", true, 5, NOW.plusSeconds(60), Set.of(Role.ROLE_SALES));

            assertThat(locked.canAttemptLoginAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("ロック期限を過ぎていれば自動的に受け付ける（解除操作を要さない）")
        void acceptsAfterLockExpires() {
            User expired = User.restore(
                1L, "s", "s@example.com", "テスト利用者", "h", true, 5, NOW.minusSeconds(1), Set.of(Role.ROLE_SALES));

            assertThat(expired.canAttemptLoginAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("ロック期限ちょうどはまだロック中とみなす")
        void stillLockedAtBoundary() {
            User boundary =
                    User.restore(
                1L, "s", "s@example.com", "テスト利用者", "h", true, 5, NOW, Set.of(Role.ROLE_SALES));

            assertThat(boundary.canAttemptLoginAt(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("認証の失敗")
    class Failure {

        @Test
        @DisplayName("失敗を数える")
        void countsFailures() {
            User once = active().withFailedAttemptAt(NOW);

            assertThat(once.failedAttempts()).isEqualTo(1);
            assertThat(once.lockedUntil()).isNull();
        }

        @Test
        @DisplayName("4 回まではロックしない")
        void doesNotLockBeforeThreshold() {
            User user = active();
            for (int i = 0; i < 4; i++) {
                user = user.withFailedAttemptAt(NOW);
            }

            assertThat(user.failedAttempts()).isEqualTo(4);
            assertThat(user.canAttemptLoginAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("5 回連続で失敗すると 15 分ロックする")
        void locksAfterFiveConsecutiveFailures() {
            User user = active();
            for (int i = 0; i < 5; i++) {
                user = user.withFailedAttemptAt(NOW);
            }

            assertThat(user.failedAttempts()).isEqualTo(5);
            assertThat(user.lockedUntil()).isEqualTo(NOW.plusSeconds(15 * 60));
            assertThat(user.canAttemptLoginAt(NOW)).isFalse();
            assertThat(user.canAttemptLoginAt(NOW.plusSeconds(15 * 60).plusSeconds(1))).isTrue();
        }
    }

    @Nested
    @DisplayName("認証の成功")
    class Success {

        @Test
        @DisplayName("成功したら失敗回数とロックを消す")
        void clearsFailureState() {
            User user = active();
            for (int i = 0; i < 3; i++) {
                user = user.withFailedAttemptAt(NOW);
            }

            User succeeded = user.withSuccessfulLogin();

            assertThat(succeeded.failedAttempts()).isZero();
            assertThat(succeeded.lockedUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("復元")
    class Restoration {

        @Test
        @DisplayName("ロール列は保持した順序に依らず同じ集合として扱う")
        void keepsRoles() {
            User multiRole = User.restore(
                1L, "u", "u@example.com", "テスト利用者", "h", true, 0, null,
                    Set.of(Role.ROLE_SALES, Role.ROLE_TRACKER));

            assertThat(multiRole.roles()).containsExactlyInAnyOrder(Role.ROLE_SALES, Role.ROLE_TRACKER);
        }

        @Test
        @DisplayName("ロールを 1 つも持たない利用者も復元できる")
        void restoresUserWithoutRoles() {
            // 不変条件を後から足すと、その列が無かったころの行が読めなくなる。
            // 復元では検査せず、ロールの要否は認可（403）で判断する。
            User noRole = User.restore(
                1L, "u", "u@example.com", "テスト利用者", "h", true, 0, null, Set.of());

            assertThat(noRole.roles()).isEmpty();
            assertThat(noRole.canAttemptLoginAt(NOW)).isTrue();
        }
    }

    @Nested
    @DisplayName("ロールの一覧")
    class Roles {

        @Test
        @DisplayName("ui_design で確定した 7 値を持つ")
        void hasSevenRoles() {
            assertThat(List.of(Role.values()))
                    .containsExactly(
                            Role.ROLE_SHIPPER,
                            Role.ROLE_SALES,
                            Role.ROLE_ROUTING,
                            Role.ROLE_HANDLER,
                            Role.ROLE_TRACKER,
                            Role.ROLE_ACCOUNTANT,
                            Role.ROLE_ADMIN);
        }
    }
}
