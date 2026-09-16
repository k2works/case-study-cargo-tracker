package com.example.cargotracker.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.auth.Role;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ロックと無効化の判断（US26 / US31）。画面や SQL に散らさず集約に置く。 */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-09-03T09:00:00Z");

    private static User user(boolean enabled, Instant lockedUntil) {
        return new User("sales01", "hash", "営業 太郎", null, Set.of(Role.ROLE_SALES),
                enabled, 0, lockedUntil);
    }

    @Test
    @DisplayName("有効でロックされていなければ入れる")
    void allowsEnabledAndUnlocked() {
        assertThat(user(true, null).canSignInAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("無効なら入れない")
    void rejectsDisabled() {
        assertThat(user(false, null).canSignInAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("ロック期限内は入れない")
    void rejectsWhileLocked() {
        assertThat(user(true, NOW.plusSeconds(60)).canSignInAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("ロック期限を過ぎたら入れる（境界は期限ちょうど）")
    void allowsAfterLockExpires() {
        assertThat(user(true, NOW).canSignInAt(NOW))
                .as("期限ちょうどは解除済みとして扱う")
                .isTrue();
        assertThat(user(true, NOW.minusSeconds(1)).canSignInAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("ロックされていない利用者は期限を持たない")
    void hasNoLockWhenNotLocked() {
        assertThat(user(true, null).isLockedAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("ロールを渡さなくても壊れない")
    void toleratesMissingRoles() {
        User withoutRoles = new User("x", "h", "n", null, null, true, 0, null);

        assertThat(withoutRoles.roles()).isEmpty();
    }
}
