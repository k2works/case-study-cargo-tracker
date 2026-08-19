package com.example.authms.application.port;

import com.example.authms.domain.model.AuthEventType;

/**
 * 認証事象の記録。
 *
 * <p>画面には失敗理由を出さない（US31）ため、何が起きたかを追える場所はここだけになる。
 * 記録に失敗しても認証は続行するが、失敗そのものは握り潰さず上位に見える形で扱う。
 */
public interface AuthAuditLogger {

    void record(String username, AuthEventType eventType, String detail);
}
