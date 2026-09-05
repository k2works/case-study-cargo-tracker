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

    /**
     * ロックの長さ。<b>非機能要件（`non_functional.md`「認証失敗 5 回で 15 分ロック」）が正典。</b>
     * ここを実装の都合で伸ばすと、設計の数字を誰も見なくなる。
     */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** 監査ログの種別（`data-model.md`「auth_db」）。 */
    private static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    private static final String LOCKED = "LOCKED";

    /** 断った理由。画面には出さず、記録にだけ残す。 */
    private static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    private static final String REASON_LOCKED = "LOCKED";
    private static final String DISABLED = "DISABLED";

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
            // 居ない利用者も「資格情報が違う」として残す。DISABLED や LOCKED と
            // 書き分けると、記録を見られたときに実在する利用者名が漏れる。
            auditFailure(request.username(), BAD_CREDENTIALS, httpRequest, now);
            return failed();
        }

        Set<Role> roles = users.findRoles(row.username()).stream()
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        User user = new User(row.username(), row.passwordHash(), row.displayName(),
                row.shipperId(), roles, row.enabled(), row.failedAttempts(), row.lockedUntil());

        if (!user.canSignInAt(now)) {
            auditFailure(user.username(),
                    user.isLockedAt(now) ? REASON_LOCKED : DISABLED, httpRequest, now);
            return failed();
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            boolean locksNow = attempts >= User.MAX_FAILED_ATTEMPTS;
            Instant lockedUntil = locksNow ? now.plus(LOCK_DURATION) : user.lockedUntil();
            users.updateSignInState(user.username(), attempts, lockedUntil, now);
            auditFailure(user.username(), BAD_CREDENTIALS, httpRequest, now);
            if (locksNow) {
                // 「失敗が 5 件ある」と「ロックした」は別の事実。後者を残さないと、
                // 運用が「いつロックされたのか」に答えられない。
                audit(user.username(), LOCKED, REASON_LOCKED, httpRequest, now);
            }
            return failed();
        }

        users.updateSignInState(user.username(), 0, null, now);
        audit(user.username(), LOGIN_SUCCESS, null, httpRequest, now);

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

    private void auditFailure(String username, String reason, HttpServletRequest request,
            Instant at) {
        audit(username, LOGIN_FAILURE, reason, request, at);
    }

    private void audit(String username, String eventType, String reason,
            HttpServletRequest request, Instant at) {
        users.insertAuditLog(new UserMapper.AuditLogRow(username, eventType, reason,
                request.getRemoteAddr(), at));
    }
}
