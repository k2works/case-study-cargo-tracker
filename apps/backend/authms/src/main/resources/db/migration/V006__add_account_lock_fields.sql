-- V006: アカウントロック機能のためのカラム追加（US00-r1）
--
-- 5 回連続ログイン失敗で 30 分ロックする機能（IT2 持越し）。
-- failed_attempts: 連続失敗回数（成功時に 0 にリセット）
-- lock_until: ロック解除時刻（NULL の場合は未ロック）

ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN lock_until TIMESTAMP NULL;
