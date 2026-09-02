package com.example.authms.domain.model;

import java.time.Instant;

/**
 * ログインの試行状況。連続失敗回数とロック期限は対で意味を持つ（US31）。
 *
 * <p>ばらばらに渡すと、期限だけを残して回数を捨てるような組み合わせが作れてしまう。
 * その状態では何度失敗してもロックが成立しない（IT2 で実際に踏んだ形）。
 *
 * @param failedAttempts 連続した失敗回数
 * @param lockedUntil ロック期限（ロックされていなければ null）
 */
public record LoginState(int failedAttempts, Instant lockedUntil) {

    /** 一度も失敗していない状態。 */
    public static LoginState clean() {
        return new LoginState(0, null);
    }
}
