-- 初期スキーマ（PostgreSQL 方言・ADR-0003）
-- 認証ユーザー。US26 のロール別アクセス制御の基盤。
-- サロゲートキー（id: BIGSERIAL）+ 業務キー（username）の UK は data-model 規約に従う。
CREATE TABLE users (
    id            BIGSERIAL   PRIMARY KEY,
    username      TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_username UNIQUE (username)
);
