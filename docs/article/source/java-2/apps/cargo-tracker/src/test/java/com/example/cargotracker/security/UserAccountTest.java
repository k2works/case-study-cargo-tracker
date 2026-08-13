package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.security.domain.model.valueobjects.Role;
import com.example.cargotracker.security.domain.model.aggregates.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link UserAccount} のロックに関する不変条件（US31）。
 *
 * <p>統合テスト（{@link AccountLockTest}）は実時刻で動くため、
 * <strong>ロック期限が切れた後の振る舞いを検証できない</strong>。30 分待つ統合テストは書けない。
 * 時刻を引数で受け取る設計にしてあるのは、まさにここを単体で固定するためである。
 */
class UserAccountTest {

    private static final Instant T0 = Instant.parse("2026-08-06T09:00:00Z");

    private static UserAccount enabledAccount() {
        return new UserAccount(
                new UserAccount.Identity(1L, "sales", "sales@example.com", "$2a$12$dummy"),
                true, Set.of(Role.SALES), 0, null);
    }

    @Test
    void 失敗が5回連続するとロックされる() {
        UserAccount account = enabledAccount();

        for (int i = 0; i < 5; i++) {
            account.recordFailure(T0);
        }

        assertThat(account.isLockedAt(T0)).isTrue();
    }

    @Test
    void 失敗が4回まではロックされない() {
        UserAccount account = enabledAccount();

        for (int i = 0; i < 4; i++) {
            account.recordFailure(T0);
        }

        assertThat(account.isLockedAt(T0)).isFalse();
    }

    @Test
    void ロック期限を過ぎるとロックが解ける() {
        UserAccount account = enabledAccount();
        for (int i = 0; i < 5; i++) {
            account.recordFailure(T0);
        }

        assertThat(account.isLockedAt(T0.plus(Duration.ofMinutes(30)))).isFalse();
    }

    @Test
    void ロック期限が切れた後の1回の失敗で再ロックされない() {
        // 「5 回連続で失敗したらロック」が不変条件である。
        // 期限切れ後に回数を持ち越すと、1 回間違えただけで 30 分締め出されることになる。
        UserAccount account = enabledAccount();
        for (int i = 0; i < 5; i++) {
            account.recordFailure(T0);
        }
        Instant afterExpiry = T0.plus(Duration.ofMinutes(31));

        account.recordFailure(afterExpiry);

        assertThat(account.failedAttempts()).isEqualTo(1);
        assertThat(account.isLockedAt(afterExpiry)).isFalse();
    }

    @Test
    void ロック期限が切れた後も5回連続で失敗すれば再びロックされる() {
        UserAccount account = enabledAccount();
        for (int i = 0; i < 5; i++) {
            account.recordFailure(T0);
        }
        Instant afterExpiry = T0.plus(Duration.ofMinutes(31));

        for (int i = 0; i < 5; i++) {
            account.recordFailure(afterExpiry);
        }

        assertThat(account.isLockedAt(afterExpiry)).isTrue();
    }

    @Test
    void 認証成功で失敗回数が戻る() {
        UserAccount account = enabledAccount();
        account.recordFailure(T0);
        account.recordFailure(T0);

        account.recordSuccess();

        assertThat(account.failedAttempts()).isZero();
        assertThat(account.isLockedAt(T0)).isFalse();
    }
}
