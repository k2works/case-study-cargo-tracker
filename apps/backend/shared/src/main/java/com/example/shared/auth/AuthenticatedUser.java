package com.example.shared.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway が検証済みのクレームとして渡した利用者（ADR-004）。
 *
 * <p>各サービスは JWT の署名を再検証しない。Gateway を通らない経路が存在しないことが前提であり、
 * その前提はネットワーク層で担保する。サービスが行うのはロールに基づく認可判定だけである。
 *
 * <p>ヘッダ名とその解釈を共有カーネルに置くのは、この 1 本の契約に ADR-004 の分担全体が
 * 乗っているため。サービスごとに定数を書き写すと、Gateway 側で名前を変えても誰も落ちない。
 */
public record AuthenticatedUser(String userId, Set<Role> roles) {

    public static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    public static final String ROLES_HEADER = "X-Authenticated-Roles";

    public static AuthenticatedUser of(String userId, String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return new AuthenticatedUser(userId, Set.of());
        }
        Set<Role> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .map(Role::of)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(userId, roles);
    }

    public boolean hasAnyRole(Role... allowed) {
        return Arrays.stream(allowed).anyMatch(roles::contains);
    }
}
