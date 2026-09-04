package com.example.cargotracker.shared.domain.auth;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.Set;

/**
 * 認証済みの利用者。Gateway が JWT から組み立て、後段サービスへ伝える。
 *
 * <p>後段サービスは署名を再検証しない（ADR-0001 決定 4 の分担）。検証を二重に置くと、
 * どちらが正かが曖昧になり、片方を直したときにもう片方が置き去りになる。</p>
 */
public record AuthenticatedUser(String username, Set<Role> roles, String shipperId) {

    public AuthenticatedUser {
        if (username == null || username.isBlank()) {
            throw new BusinessRuleViolation("利用者名は必須です");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean hasAny(Role... candidates) {
        for (Role role : candidates) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
