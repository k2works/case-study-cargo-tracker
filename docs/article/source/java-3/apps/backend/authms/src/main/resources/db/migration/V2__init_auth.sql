-- authms の実スキーマ（data-model.md の auth_db に対応）。
-- 既にデプロイ済みの環境があるため V1 は変更せず、実テーブルは V2 で追加する。

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(200) NOT NULL UNIQUE,
    -- 画面に出す呼び名。利用者 ID やメールアドレスで代用すると、誰として入っているかが読みにくい
    display_name    VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    -- 失敗回数とロック期限は永続化する。監査ログからの再導出は、ログの欠落で
    -- 「ロックされていないこと」になり保護が静かに外れる（US31）
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id),
    role    VARCHAR(50) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role)
);

-- 未登録の利用者名での試行も記録するため users への外部キーは張らない
CREATE TABLE auth_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    event_type  VARCHAR(30)  NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    detail      VARCHAR(500)
);

CREATE INDEX idx_auth_audit_log_username ON auth_audit_log (username, occurred_at);
