package com.example.cargotracker.auth.interfaces.rest;

import com.example.cargotracker.auth.infrastructure.persistence.UserMapper;
import com.example.cargotracker.shared.domain.auth.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 利用者管理（S90 / US31）。管理者だけが読み、ロックを解ける。
 *
 * <p>ロールは Gateway が JWT から取り出して {@code X-Auth-Roles} で伝える
 * （ADR-0001 決定 4）。ここで署名を再検証しない。</p>
 */
@RestController
@RequestMapping("/api/v1/auth/admin/users")
public class AdminUserController {

    private final UserMapper users;
    private final Clock clock;

    public AdminUserController(UserMapper users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public record AdminUserView(String username, String displayName, List<String> roles,
            boolean enabled, int failedAttempts, Instant lockedUntil, boolean locked) {
    }

    @GetMapping
    public ResponseEntity<Map<String, List<AdminUserView>>> list(
            @RequestHeader(name = "X-Auth-Roles", required = false) String roles) {
        requireAdmin(roles);
        Instant now = clock.instant();
        return ResponseEntity.ok(Map.of("users", users.findAllForAdmin().stream()
                .map(row -> toView(row, now))
                .toList()));
    }

    /**
     * ロックを解く。
     *
     * <p>居ない利用者でも {@code 204} を返す。{@code 404} と出し分けると、
     * 管理画面を踏み台にして利用者名を総当たりできる。解除は「その利用者が
     * ロックされていない状態にする」ことなので、居なければ既にその状態である。</p>
     */
    @PostMapping("/{username}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable String username,
            @RequestHeader(name = "X-Auth-Roles", required = false) String roles,
            HttpServletRequest request) {
        requireAdmin(roles);
        Instant now = clock.instant();
        if (users.unlock(username, now) > 0) {
            users.insertAuditLog(new UserMapper.AuditLogRow(username, "UNLOCKED", null,
                    request.getRemoteAddr(), now));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 認可は入力検証より先に置く。あとに置くと、権限の無い相手に
     * 入力仕様（どの項目が要るか）を教えてしまう。
     */
    private void requireAdmin(String rolesHeader) {
        if (rolesHeader == null || Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .noneMatch(Role.ROLE_ADMIN.name()::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static AdminUserView toView(UserMapper.AdminUserRow row, Instant now) {
        return new AdminUserView(
                row.username(),
                row.displayName(),
                row.roles() == null ? List.of() : List.of(row.roles().split(",")),
                row.enabled(),
                row.failedAttempts(),
                row.lockedUntil(),
                row.lockedUntil() != null && now.isBefore(row.lockedUntil()));
    }
}
