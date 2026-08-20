package com.example.authms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.PasswordVerifier;
import com.example.authms.application.port.TokenIssuer;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.AuthEventType;
import com.example.shared.auth.Role;
import com.example.authms.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ログイン")
class LoginUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
    private static final String PASSWORD = "correct-password";
    private static final String HASH = "hash-of-correct-password";

    private final Map<String, User> stored = new HashMap<>();
    private final List<String> auditTrail = new ArrayList<>();

    private final UserRepository users = new UserRepository() {
        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(stored.get(username));
        }

        @Override
        public void updateLoginState(User user) {
            stored.put(user.username(), user);
        }

        @Override
        public User recordFailedAttempt(User user, Instant now) {
            User failed = user.withFailedAttemptAt(now);
            stored.put(failed.username(), failed);
            return failed;
        }
    };

    private final AuthAuditLogger auditLogger =
            (username, eventType, detail) -> auditTrail.add(username + ":" + eventType);

    private final PasswordVerifier passwordVerifier =
            (raw, hash) -> HASH.equals(hash) && PASSWORD.equals(raw);

    private final TokenIssuer tokenIssuer = user -> "token-for-" + user.username();

    /** 時間を進められる Clock。固定 Clock だとロックの自動解除を通せない。 */
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Tokyo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime.get();
        }
    };

    private final LoginUseCase useCase =
            new LoginUseCase(users, auditLogger, passwordVerifier, tokenIssuer, clock);

    @BeforeEach
    void setUp() {
        stored.put("sales01", User.restore(
                1L, "sales01", "sales@example.com", "テスト利用者", HASH, true, 0, null, Set.of(Role.ROLE_SALES)));
    }

    @Nested
    @DisplayName("成功")
    class Success {

        @Test
        @DisplayName("トークンと画面が必要とする情報を返す")
        void issuesToken() {
            LoginResult result = useCase.login("sales01", PASSWORD).orElseThrow();

            assertThat(result.token()).isEqualTo("token-for-sales01");
            assertThat(result.userId()).isEqualTo("sales01");
            assertThat(result.displayName()).isEqualTo("テスト利用者");
            assertThat(result.roles()).containsExactly(Role.ROLE_SALES);
        }

        @Test
        @DisplayName("それまでの失敗回数を消す")
        void clearsPreviousFailures() {
            useCase.login("sales01", "wrong");
            useCase.login("sales01", "wrong");

            useCase.login("sales01", PASSWORD);

            assertThat(stored.get("sales01").failedAttempts()).isZero();
        }

        @Test
        @DisplayName("監査ログに残す")
        void recordsAudit() {
            useCase.login("sales01", PASSWORD);

            assertThat(auditTrail).contains("sales01:" + AuthEventType.LOGIN_SUCCESS);
        }
    }

    @Nested
    @DisplayName("ロックの自動解除")
    class LockRelease {

        private void failFiveTimes() {
            for (int i = 0; i < 5; i++) {
                useCase.login("sales01", "wrong");
            }
        }

        @Test
        @DisplayName("15 分が過ぎればログインできる（解除の操作は要らない）")
        void allowsLoginAfterLockDuration() {
            failFiveTimes();
            assertThat(useCase.login("sales01", PASSWORD)).as("ロック直後は入れない").isEmpty();

            currentTime.set(NOW.plusSeconds(15 * 60).plusSeconds(1));

            assertThat(useCase.login("sales01", PASSWORD))
                    .as("15 分経っても入れない。自動解除が名目だけになっている")
                    .isPresent();
        }

        @Test
        @DisplayName("解除後に 1 回間違えても即座に再ロックしない")
        void doesNotRelockImmediatelyAfterRelease() {
            failFiveTimes();
            currentTime.set(NOW.plusSeconds(15 * 60).plusSeconds(1));

            useCase.login("sales01", "wrong");

            // 数え直さないと、正規の利用者は事実上パスワードを 1 回も間違えられなくなる
            assertThat(useCase.login("sales01", PASSWORD)).isPresent();
        }

        @Test
        @DisplayName("解除後も 5 回続けて失敗すれば再びロックする")
        void locksAgainAfterFiveFailures() {
            failFiveTimes();
            currentTime.set(NOW.plusSeconds(15 * 60).plusSeconds(1));

            failFiveTimes();

            assertThat(useCase.login("sales01", PASSWORD)).isEmpty();
        }
    }

    @Nested
    @DisplayName("失敗")
    class Failure {

        @Test
        @DisplayName("パスワードが違えば認証しない")
        void rejectsWrongPassword() {
            assertThat(useCase.login("sales01", "wrong")).isEmpty();
            assertThat(stored.get("sales01").failedAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("存在しない利用者でも同じく空を返す（存在の有無を漏らさない）")
        void rejectsUnknownUser() {
            assertThat(useCase.login("nobody", PASSWORD)).isEmpty();
        }

        @Test
        @DisplayName("存在しない利用者への試行も監査ログに残す")
        void recordsUnknownUserAttempt() {
            useCase.login("nobody", PASSWORD);

            assertThat(auditTrail).contains("nobody:" + AuthEventType.LOGIN_FAILURE);
        }

        @Test
        @DisplayName("5 回連続で失敗するとロックし、正しいパスワードでも受け付けない")
        void locksAfterFiveFailures() {
            for (int i = 0; i < 5; i++) {
                useCase.login("sales01", "wrong");
            }

            assertThat(stored.get("sales01").lockedUntil()).isNotNull();
            assertThat(useCase.login("sales01", PASSWORD))
                    .as("ロック中に正しいパスワードで入れてしまう")
                    .isEmpty();
            assertThat(auditTrail).contains("sales01:" + AuthEventType.LOCKED);
        }

        @Test
        @DisplayName("無効化された利用者は照合するまでもなく拒否する")
        void rejectsDisabledUser() {
            stored.put("retired", User.restore(
                2L, "retired", "r@example.com", "テスト利用者", HASH, false, 0, null, Set.of(Role.ROLE_SALES)));

            assertThat(useCase.login("retired", PASSWORD)).isEmpty();
            assertThat(auditTrail).contains("retired:" + AuthEventType.DISABLED_ATTEMPT);
        }

        @Test
        @DisplayName("ロック中の試行では失敗回数をさらに増やさない")
        void doesNotCountAttemptsWhileLocked() {
            for (int i = 0; i < 5; i++) {
                useCase.login("sales01", "wrong");
            }
            int attemptsAtLock = stored.get("sales01").failedAttempts();

            useCase.login("sales01", "wrong");

            // ロック中の試行で回数を積むと、解除後すぐ再ロックする状態が続き
            // 正規の利用者がいつまでも入れなくなる
            assertThat(stored.get("sales01").failedAttempts()).isEqualTo(attemptsAtLock);
        }
    }
}
