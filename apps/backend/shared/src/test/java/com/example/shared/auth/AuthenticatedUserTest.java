package com.example.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gateway が渡した認証済み利用者")
class AuthenticatedUserTest {

    @Test
    @DisplayName("ロールのヘッダを解釈する")
    void parsesRoles() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", "ROLE_SALES,ROLE_TRACKER");

        assertThat(user.hasAnyRole(Role.ROLE_SALES)).isTrue();
        assertThat(user.hasAnyRole(Role.ROLE_TRACKER)).isTrue();
        assertThat(user.hasAnyRole(Role.ROLE_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("空白を含むヘッダでも取りこぼさない")
    void toleratesSpaces() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", " ROLE_SALES , ROLE_TRACKER ");

        assertThat(user.hasAnyRole(Role.ROLE_TRACKER)).isTrue();
    }

    @Test
    @DisplayName("ロールが無ければどの権限も持たない")
    void hasNoRoleWhenHeaderIsEmpty() {
        // 「載っていないものを通す」向きにすると、ヘッダが落ちた呼び出しが全権限を得る
        assertThat(AuthenticatedUser.of("sales01", "").hasAnyRole(Role.ROLE_SALES)).isFalse();
        assertThat(AuthenticatedUser.of("sales01", null).hasAnyRole(Role.ROLE_SALES)).isFalse();
    }

    @Test
    @DisplayName("知らないロール名は権限として扱わない")
    void ignoresUnknownRole() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", "ROLE_SUPERUSER");

        assertThat(user.roles()).isEmpty();
    }

    @Test
    @DisplayName("いずれかのロールを持てば許可する")
    void allowsAnyOfGivenRoles() {
        AuthenticatedUser user = AuthenticatedUser.of("handler01", "ROLE_HANDLER");

        assertThat(user.hasAnyRole(Role.ROLE_HANDLER, Role.ROLE_TRACKER)).isTrue();
        assertThat(user.hasAnyRole(Role.ROLE_SALES, Role.ROLE_ACCOUNTANT)).isFalse();
    }
}
