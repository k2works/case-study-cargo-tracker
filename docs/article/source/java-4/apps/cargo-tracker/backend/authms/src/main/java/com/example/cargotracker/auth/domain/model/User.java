package com.example.cargotracker.auth.domain.model;

import com.example.cargotracker.shared.domain.auth.Role;
import java.time.Instant;
import java.util.Set;

/**
 * 利用者（状態保存。Event Sourcing は使わない。ADR-0001 決定 2）。
 *
 * <p>ロックの判断をここに置く。画面や SQL に散らすと、片方だけ直したときに
 * ロックが効かない経路が残る。</p>
 */
public record User(
        String username,
        String passwordHash,
        String displayName,
        String shipperId,
        Set<Role> roles,
        boolean enabled,
        int failedAttempts,
        Instant lockedUntil) {

    /** 続けて失敗できる回数。超えるとロックする（US31）。 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    public User {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** ログインできる状態か。理由は返さない（利用者に伝えるのは同一メッセージ）。 */
    public boolean canSignInAt(Instant now) {
        return enabled && !isLockedAt(now);
    }
}
