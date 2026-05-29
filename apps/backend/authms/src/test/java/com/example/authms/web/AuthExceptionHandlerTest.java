package com.example.authms.web;

import com.example.authms.application.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    @DisplayName("AuthenticationException → 401 認証失敗")
    void 認証失敗は401() {
        ProblemDetail detail = handler.handleAuthenticationException(
                new AuthService.AuthenticationException("ユーザーが見つかりません"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(detail.getTitle()).isEqualTo("認証失敗");
        assertThat(detail.getDetail()).isEqualTo("ユーザーが見つかりません");
    }

    @Test
    @DisplayName("AccountLockedException → 423 アカウントロック")
    void ロックは423() {
        ProblemDetail detail = handler.handleAccountLockedException(
                new AuthService.AccountLockedException("ロック中"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.LOCKED.value());
        assertThat(detail.getTitle()).isEqualTo("アカウントロック");
        assertThat(detail.getDetail()).isEqualTo("ロック中");
    }
}
