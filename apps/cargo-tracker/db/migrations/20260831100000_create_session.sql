-- migrate:up

-- IT5 task 1.2 セッション認証テーブル (ADR-0010)
--
-- Servant Auth の opaque Cookie 認証で使用。Cookie 値は 256bit ランダム を
-- base64url エンコードした 44 文字を保存する。サーバ側で失効即時反映可能。
--
-- 有効期限: 8 時間 (business day)、AuthHandler で都度延長 (sliding window)
-- ログアウト: DELETE session WHERE session_token = ? で即時無効化

CREATE TABLE session (
    id             BIGSERIAL PRIMARY KEY,
    session_token  VARCHAR(64) NOT NULL UNIQUE,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_user       ON session (user_id);
CREATE INDEX idx_session_expires_at ON session (expires_at);

-- migrate:down

DROP TABLE session;
