-- handlingms の初期スキーマ。
-- 実テーブルは開発フェーズで data-model.md に従って追加する。
-- Flyway が管理対象のスキーマを認識できるよう、空マイグレーションを置く。
CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id          INTEGER     NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_schema_bootstrap PRIMARY KEY (id)
);
