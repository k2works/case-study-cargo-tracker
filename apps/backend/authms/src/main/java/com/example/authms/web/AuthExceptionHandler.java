package com.example.authms.web;

import com.example.authms.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthService.AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthService.AuthenticationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("認証失敗");
        return detail;
    }

    @ExceptionHandler(AuthService.AccountLockedException.class)
    public ProblemDetail handleAccountLockedException(AuthService.AccountLockedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
        detail.setTitle("アカウントロック");
        return detail;
    }
}
