package com.example.cargotracker.authms.interfaces.rest;

import com.example.cargotracker.authms.application.AuthCommandService;
import com.example.cargotracker.authms.application.AuthQueryService;
import com.example.cargotracker.authms.domain.model.Role;
import com.example.cargotracker.authms.domain.model.UserName;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.infrastructure.security.JwtTokenProvider;
import com.example.cargotracker.authms.interfaces.rest.dto.LoginRequest;
import com.example.cargotracker.authms.interfaces.rest.dto.RegisterRequest;
import com.example.cargotracker.authms.interfaces.rest.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "認証 API")
public class AuthController {

    private static final String MESSAGE_KEY = "message";

    private final AuthCommandService commandService;
    private final AuthQueryService queryService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthCommandService commandService, AuthQueryService queryService,
                          UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    @Operation(summary = "ユーザー登録")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest request) {
        try {
            Role role = request.role() != null ? Role.valueOf(request.role()) : Role.ROLE_SHIPPER;
            commandService.register(request.username(), request.email(), request.password(), role);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(MESSAGE_KEY, "ユーザー登録が完了しました"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "ログイン・JWT 取得")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        try {
            String token = queryService.login(request.username(), request.password());
            String username = jwtTokenProvider.getUsernameFromToken(token);
            List<String> roles = jwtTokenProvider.getRolesFromToken(token);
            return ResponseEntity.ok(new TokenResponse(token, username, roles));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "認証済みユーザー情報取得")
    public ResponseEntity<Object> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(MESSAGE_KEY, "認証が必要です"));
        }
        String username = authentication.getName();
        return userRepository.findByUsername(new UserName(username))
                .map(user -> {
                    List<String> roles = user.getRoles().stream()
                            .map(Role::name)
                            .toList();
                    return ResponseEntity.ok().<Object>body(Map.of(
                            "username", user.username().value(),
                            "email", user.email().value(),
                            "roles", roles
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(MESSAGE_KEY, "ユーザーが見つかりません")));
    }
}
