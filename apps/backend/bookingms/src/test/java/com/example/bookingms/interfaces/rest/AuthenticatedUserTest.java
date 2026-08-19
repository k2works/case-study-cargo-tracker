package com.example.bookingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gateway が渡した認証済み利用者")
class AuthenticatedUserTest {

    @Test
    @DisplayName("ロールのヘッダを解釈する")
    void parsesRoles() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", "ROLE_SALES,ROLE_TRACKER");

        assertThat(user.hasAnyRole("ROLE_SALES")).isTrue();
        assertThat(user.hasAnyRole("ROLE_TRACKER")).isTrue();
        assertThat(user.hasAnyRole("ROLE_ADMIN")).isFalse();
    }

    @Test
    @DisplayName("空白を含むヘッダでも取りこぼさない")
    void toleratesSpaces() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", " ROLE_SALES , ROLE_TRACKER ");

        assertThat(user.hasAnyRole("ROLE_TRACKER")).isTrue();
    }

    @Test
    @DisplayName("ロールが無ければどの権限も持たない")
    void hasNoRoleWhenHeaderIsEmpty() {
        // 「載っていないものを通す」向きにすると、ヘッダが落ちた呼び出しが全権限を得る
        AuthenticatedUser user = AuthenticatedUser.of("sales01", "");

        assertThat(user.hasAnyRole("ROLE_SALES")).isFalse();
    }

    @Test
    @DisplayName("知らないロール名は権限として扱わない")
    void ignoresUnknownRole() {
        AuthenticatedUser user = AuthenticatedUser.of("sales01", "ROLE_SUPERUSER");

        assertThat(user.hasAnyRole("ROLE_SALES")).isFalse();
    }
}
