package com.example.authms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.AuthEventType;
import com.example.authms.domain.model.LoginState;
import com.example.authms.domain.model.User;
import com.example.authms.domain.model.UserIdentity;
import com.example.shared.auth.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 管理者によるロック解除（US32）。 */
@DisplayName("ロックの解除")
class UnlockAccountUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-22T02:00:00Z");

    /** 監査ログの 1 行。誰に・何が・誰の操作で、を組で見る。 */
    private record AuditEntry(String username, AuthEventType eventType, String detail,
            String actor) {
    }

    private final List<AuditEntry> audit = new ArrayList<>();
    private final List<User> saved = new ArrayList<>();

    private User stored = locked(NOW.plus(Duration.ofMinutes(10)));

    private static User locked(Instant lockedUntil) {
        return User.restore(1L,
                new UserIdentity("sales01", "sales01@example.com", "山田太郎", "hash"),
                true, new LoginState(5, lockedUntil), Set.of(Role.ROLE_SALES));
    }

    private final UserRepository users = new UserRepository() {
        @Override
        public Optional<User> findByUsername(String username) {
            return "sales01".equals(username) ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public void updateLoginState(User user) {
            saved.add(user);
        }

        @Override
        public User recordFailedAttempt(User user, Instant now) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }

        @Override
        public List<User> findLocked(Instant now) {
            return stored.lockedUntil() != null && now.isBefore(stored.lockedUntil())
                    ? List.of(stored)
                    : List.of();
        }

        @Override
        public Optional<Long> findLinkedShipperId(String username) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }
    };

    private final AuthAuditLogger auditLogger =
            (username, eventType, detail, actor) ->
                    audit.add(new AuditEntry(username, eventType, detail, actor));

    private final UnlockAccountUseCase useCase = new UnlockAccountUseCase(users, auditLogger,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("ロック中のアカウントを一覧できる")
    void listsLockedAccounts() {
        assertThat(useCase.lockedAccounts()).extracting(User::username).containsExactly("sales01");
    }

    /**
     * 期限切れは出さない。
     *
     * <p>期限が切れたロックは解除操作なしで受け付けが戻っている。一覧に出すと管理者は
     * 要らない作業をする。
     */
    @Test
    @DisplayName("期限が切れたロックは一覧に出さない")
    void excludesExpiredLocks() {
        stored = locked(NOW.minus(Duration.ofMinutes(1)));

        assertThat(useCase.lockedAccounts()).isEmpty();
    }

    /**
     * <strong>失敗回数も白紙に戻す</strong>（US32-2）。
     *
     * <p>期限だけを消すと、次に 1 回失敗した時点でまた 5 回目に達してロックされる。
     * 解除した直後に同じ状態へ戻るのでは、解除にならない。
     */
    @Test
    @DisplayName("解除すると、期限も失敗回数も白紙に戻る")
    void clearsBothLockAndAttempts() {
        User unlocked = useCase.unlock("sales01", "admin01").orElseThrow();

        assertThat(unlocked.lockedUntil()).isNull();
        assertThat(unlocked.failedAttempts()).isZero();
        assertThat(unlocked.canAttemptLoginAt(NOW)).isTrue();
        assertThat(saved).hasSize(1);
    }

    /**
     * US32-3。<strong>誰が・いつ・どのアカウントを</strong>が残る。
     *
     * <p>画面には認証の失敗理由を出さないため（US31）、何が起きたかを追える場所は
     * 監査ログだけである。そこに「誰が解除したか」が無いと、あとから誰にも説明できない。
     */
    @Test
    @DisplayName("解除は監査ログに残り、誰が解除したかが分かる")
    void recordsWhoUnlocked() {
        useCase.unlock("sales01", "admin01");

        assertThat(audit).containsExactly(
                new AuditEntry("sales01", AuthEventType.UNLOCKED, "管理者による解除", "admin01"));
    }

    /**
     * 解除は冪等にする。
     *
     * <p>管理者が一覧を見てから押すまでの間に期限が切れることは普通に起こる。そこで失敗を
     * 返しても管理者にできることは無い。
     */
    @Test
    @DisplayName("ロックされていないアカウントの解除も断らない")
    void isIdempotent() {
        stored = User.restore(1L,
                new UserIdentity("sales01", "sales01@example.com", "山田太郎", "hash"),
                true, LoginState.clean(), Set.of(Role.ROLE_SALES));

        assertThat(useCase.unlock("sales01", "admin01")).isPresent();
    }

    @Test
    @DisplayName("いないアカウントの解除は空を返す")
    void returnsEmptyForUnknownUser() {
        assertThat(useCase.unlock("nobody", "admin01")).isEmpty();
        assertThat(saved).as("いないのに保存している").isEmpty();
        assertThat(audit).as("いないのに記録している").isEmpty();
    }
}
