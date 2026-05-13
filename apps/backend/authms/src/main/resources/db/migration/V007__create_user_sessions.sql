-- V007: user_sessions テーブル作成（US00-r2 ログアウト）
--
-- JWT トークンの jti（JWT ID）を主キーとし、revoked フラグでログアウトを実装する。
-- JwtAuthenticationFilter が認証時に revoked を確認する。
--
-- data-model.md の Auth DB 定義に準拠（jti を PK、session_id は将来用途のため省略）。

CREATE TABLE IF NOT EXISTS user_sessions (
    jti        VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    issued_at  TIMESTAMP    NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_revoked ON user_sessions (user_id, revoked);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at   ON user_sessions (expires_at);
