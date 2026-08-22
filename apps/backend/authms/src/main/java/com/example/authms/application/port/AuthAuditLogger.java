package com.example.authms.application.port;

import com.example.authms.domain.model.AuthEventType;

/**
 * 認証事象の記録。
 *
 * <p>画面には失敗理由を出さない（US31）ため、何が起きたかを追える場所はここだけになる。
 * 記録に失敗しても認証は続行するが、失敗そのものは握り潰さず上位に見える形で扱う。
 */
public interface AuthAuditLogger {

    /**
     * 本人の操作を記録する（ログイン・ログアウト等）。操作者は対象と同じである。
     */
    default void log(String username, AuthEventType eventType, String detail) {
        log(username, eventType, detail, null);
    }

    /**
     * 記録する。
     *
     * @param username 対象の利用者（誰に起きたか）
     * @param actor 操作した利用者（誰がやったか）。<strong>本人の操作なら {@code null}</strong>。
     *     管理者による解除のように<strong>対象と操作者が違う</strong>事象では必ず渡す（US32-3）。
     *     渡さないと「誰が解除したか」が残らず、監査に答えられない
     */
    void log(String username, AuthEventType eventType, String detail, String actor);
}
