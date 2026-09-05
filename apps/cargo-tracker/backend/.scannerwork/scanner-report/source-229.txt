package com.example.cargotracker.shared.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    @DisplayName("持っているロールを答える")
    void answersHeldRoles() {
        var user = new AuthenticatedUser("sales01", Set.of(Role.ROLE_SALES), null);

        assertThat(user.has(Role.ROLE_SALES)).isTrue();
        assertThat(user.has(Role.ROLE_ACCOUNTANT)).isFalse();
    }

    @Test
    @DisplayName("いずれかを持てば真")
    void answersAnyRole() {
        var user = new AuthenticatedUser("sales01", Set.of(Role.ROLE_SALES), null);

        assertThat(user.hasAny(Role.ROLE_ACCOUNTANT, Role.ROLE_SALES)).isTrue();
        assertThat(user.hasAny(Role.ROLE_ACCOUNTANT, Role.ROLE_TRACKER)).isFalse();
        assertThat(user.hasAny()).as("候補が無ければ偽").isFalse();
    }

    @Test
    @DisplayName("ロールが無くても壊れない")
    void toleratesMissingRoles() {
        assertThat(new AuthenticatedUser("x", null, null).roles()).isEmpty();
    }

    @Test
    @DisplayName("利用者名は必須")
    void requiresUsername() {
        assertThatThrownBy(() -> new AuthenticatedUser("  ", Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUser(null, Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
