package com.example.cargotracker.authms.domain.model;

/**
 * アカウントがロック中であることを示す例外（US00-r1）。
 *
 * <p>5 回連続ログイン失敗後の 30 分間に発生する。
 * Controller 層で 423 Locked にマッピングする。</p>
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
