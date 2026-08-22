package com.example.authms.interfaces.rest;

import com.example.authms.domain.model.User;
import java.time.Instant;

/**
 * ロック中のアカウント 1 件（US32-1）。
 *
 * <p><strong>パスワードのハッシュもメールアドレスも返さない。</strong>管理者が解除の判断を
 * するのに要るのは「誰が・いつまでロックされているか」だけである。要らないものを返すと、
 * 画面の不具合や記録の流出でそのまま漏れる。
 *
 * @param username 利用者 ID
 * @param displayName 画面に出す呼び名
 * @param failedAttempts 連続した失敗回数
 * @param lockedUntil ロック期限
 */
public record LockedAccountResponse(
        String username,
        String displayName,
        int failedAttempts,
        Instant lockedUntil) {

    public static LockedAccountResponse from(User user) {
        return new LockedAccountResponse(user.username(), user.displayName(),
                user.failedAttempts(), user.lockedUntil());
    }
}
