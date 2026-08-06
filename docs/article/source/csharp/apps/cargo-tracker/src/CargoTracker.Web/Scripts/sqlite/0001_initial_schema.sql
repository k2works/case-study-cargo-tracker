-- 初期スキーマ（SQLite 方言・ADR-0003）
-- 認証ユーザー。US26 のロール別アクセス制御の基盤。
-- サロゲートキー（id）+ 業務キー（username）の UK は data-model 規約に従う。
CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL,
    password_hash TEXT    NOT NULL,
    role          TEXT    NOT NULL,
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL,
    version       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_username UNIQUE (username)
);
