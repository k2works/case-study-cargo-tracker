package com.example.cargotracker.auth.interfaces.rest;

import com.example.cargotracker.auth.domain.model.User;
import com.example.cargotracker.auth.infrastructure.persistence.UserMapper;
import com.example.cargotracker.auth.infrastructure.security.JwtIssuer;
import com.example.cargotracker.shared.domain.auth.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ログイン（UC20 / US26）。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * 失敗時に返す唯一のメッセージ。
     *
     * <p>「利用者が居ない」「パスワードが違う」「ロック中」を出し分けると、
     * 攻撃者に利用者名の存在を教えてしまう。</p>
     */
    private static final String SIGN_IN_FAILED = "利用者名またはパスワードが正しくありません";

    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UserMapper users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final Clock clock;

    public AuthController(UserMapper users, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.clock = clock;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username, String displayName,
            List<String> roles, String shipperId) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        Instant now = clock.instant();
        UserMapper.UserRow row = users.findByUsername(request.username());

        if (row == null) {
            audit(request.username(), false, httpRequest, now);
            return failed();
        }

        Set<Role> roles = users.findRoles(row.username()).stream()
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        User user = new User(row.username(), row.passwordHash(), row.displayName(),
                row.shipperId(), roles, row.enabled(), row.failedAttempts(), row.lockedUntil());

        if (!user.canSignInAt(now)) {
            audit(user.username(), false, httpRequest, now);
            return failed();
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            Instant lockedUntil = attempts >= User.MAX_FAILED_ATTEMPTS
                    ? now.plus(LOCK_DURATION)
                    : user.lockedUntil();
            users.updateSignInState(user.username(), attempts, lockedUntil, now);
            audit(user.username(), false, httpRequest, now);
            return failed();
        }

        users.updateSignInState(user.username(), 0, null, now);
        audit(user.username(), true, httpRequest, now);

        return ResponseEntity.ok(new LoginResponse(
                jwtIssuer.issue(user.username(), user.roles(), user.shipperId()),
                user.username(),
                user.displayName(),
                user.roles().stream().map(Role::name).sorted().toList(),
                user.shipperId()));
    }

    private ResponseEntity<Map<String, String>> failed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "SIGN_IN_FAILED", "message", SIGN_IN_FAILED));
    }

    private void audit(String username, boolean succeeded, HttpServletRequest request, Instant at) {
        users.insertAuditLog(new UserMapper.AuditLogRow(username, "SIGN_IN", succeeded,
                request.getRemoteAddr(), at));
    }
}
