package com.example.bookingms.interfaces.rest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway が検証済みのクレームとして渡した利用者（ADR-004）。
 *
 * <p>このサービスは JWT の署名を再検証しない。Gateway を通らない経路が存在しないことが前提であり、
 * その前提はネットワーク層で担保する。ここで行うのはロールに基づく認可判定だけである。
 */
public record AuthenticatedUser(String userId, Set<String> roles) {

    public static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    public static final String ROLES_HEADER = "X-Authenticated-Roles";

    public static AuthenticatedUser of(String userId, String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return new AuthenticatedUser(userId, Set.of());
        }
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(userId, roles);
    }

    public boolean hasAnyRole(String... allowed) {
        return Arrays.stream(allowed).anyMatch(roles::contains);
    }
}
