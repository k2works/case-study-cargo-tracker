package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.commandservices.AuthCommandService;
import com.example.authms.domain.model.aggregates.User;
import com.example.authms.domain.model.aggregates.UserRepository;
import com.example.authms.domain.model.valueobjects.Role;
import com.example.authms.infrastructure.security.JwtTokenProvider;
import com.example.authms.interfaces.rest.dto.LoginRequest;
import com.example.authms.interfaces.rest.dto.RegisterRequest;
import com.example.authms.interfaces.rest.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthCommandService authCommandService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthCommandService authCommandService,
                          UserRepository userRepository,
                          JwtTokenProvider jwtTokenProvider) {
        this.authCommandService = authCommandService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            String token = authCommandService.login(request.username(), request.password());
            String username = jwtTokenProvider.getUsernameFromToken(token);
            List<String> roles = jwtTokenProvider.getRolesFromToken(token);
            return ResponseEntity.ok(new TokenResponse(token, username, roles));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            Role role = request.role() != null ? Role.valueOf(request.role()) : Role.ROLE_SHIPPER;
            User user = authCommandService.register(
                    request.username(), request.email(), request.password(), role);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "id", user.getId(),
                            "username", user.getUsername().getValue(),
                            "email", user.getEmail().getValue()
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "認証が必要です"));
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(user -> {
                    List<String> roles = user.getRoles().stream()
                            .map(Role::name)
                            .toList();
                    return ResponseEntity.ok(Map.of(
                            "username", user.getUsername().getValue(),
                            "email", user.getEmail().getValue(),
                            "roles", roles
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "ユーザーが見つかりません")));
    }
}
