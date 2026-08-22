package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.UnlockAccountUseCase;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * ロックされたアカウントの管理（US32）。
 *
 * <p><strong>システム管理者だけが使える。</strong>他のロールに開くと、誰でも他人のロックを
 * 外せることになり、[ADR-004] のアカウント保護（US31）が意味を失う。
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

    private final UnlockAccountUseCase unlockAccount;

    public AdminAccountController(UnlockAccountUseCase unlockAccount) {
        this.unlockAccount = unlockAccount;
    }

    /**
     * いまロックされているアカウント（US32-1）。
     *
     * <p><strong>認可は入力の検査より先に置く</strong>（[ADR-016]）。
     */
    @GetMapping("/locked")
    public List<LockedAccountResponse> locked(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAdmin(userId, roles);

        return unlockAccount.lockedAccounts().stream()
                .map(LockedAccountResponse::from)
                .toList();
    }

    /**
     * 解除する（US32-2・US32-3）。
     *
     * <p>解除した管理者を記録に残す。<strong>本人の操作ではない</strong>ため、
     * 対象（{@code username}）と操作者を分けて渡す。
     */
    @PostMapping("/{username}/unlock")
    public LockedAccountResponse unlock(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String username) {
        requireAdmin(userId, roles);

        return unlockAccount.unlock(username, userId)
                .map(LockedAccountResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "指定されたアカウントが見つかりません"));
    }

    private void requireAdmin(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
