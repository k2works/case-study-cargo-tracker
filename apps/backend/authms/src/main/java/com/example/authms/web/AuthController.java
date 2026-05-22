package com.example.authms.web;

import com.example.authms.application.AuthService;
import com.example.authms.domain.User;
import com.example.authms.security.JwtTokenProvider;
import com.example.authms.web.dto.LoginRequest;
import com.example.authms.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request.username(), request.password());
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token, user.getRole().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT はステートレスのためサーバー側処理なし（クライアント側でトークン削除）
        return ResponseEntity.noContent().build();
    }
}
