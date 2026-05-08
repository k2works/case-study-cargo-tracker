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
    private static final String MESSAGE_KEY = "message";

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
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        try {
            String token = authCommandService.login(request.username(), request.password());
            String username = jwtTokenProvider.getUsernameFromToken(token);
            List<String> roles = jwtTokenProvider.getRolesFromToken(token);
            return ResponseEntity.ok().body(new TokenResponse(token, username, roles));
        } catch (IllegalArgumentException error) {
            return errorResponse(HttpStatus.UNAUTHORIZED, error.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest request) {
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
        } catch (IllegalArgumentException error) {
            return errorResponse(HttpStatus.CONFLICT, error.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(Authentication authentication) {
        if (authentication == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "認証が必要です");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(user -> {
                    List<String> roles = user.getRoles().stream()
                            .map(Role::name)
                            .toList();
                    return ResponseEntity.ok().<Object>body(Map.of(
                            "username", user.getUsername().getValue(),
                            "email", user.getEmail().getValue(),
                            "roles", roles
                    ));
                })
                .orElseGet(() -> errorResponse(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(MESSAGE_KEY, message));
    }
}
