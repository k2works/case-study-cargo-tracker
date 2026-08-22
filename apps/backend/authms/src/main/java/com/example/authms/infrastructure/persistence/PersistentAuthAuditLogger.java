package com.example.authms.infrastructure.persistence;

import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.domain.model.AuthEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 認証事象を追記専用テーブルに残す。
 *
 * <p>画面には失敗理由を出さない（US31）ため、ここが唯一の手がかりになる。記録に失敗しても
 * 認証自体は続けるが、失敗を黙って捨てるとその手がかりごと消えるので必ずログに出す。
 */
@Component
public class PersistentAuthAuditLogger implements AuthAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(PersistentAuthAuditLogger.class);

    private final AuthAuditLogMapper mapper;

    public PersistentAuthAuditLogger(AuthAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void log(String username, AuthEventType eventType, String detail, String actor) {
        try {
            mapper.insert(username, eventType.name(), detail, actor);
        } catch (RuntimeException e) {
            log.error("認証監査ログの記録に失敗しました: username={}, eventType={}, actor={}",
                    username, eventType, actor, e);
        }
    }
}
