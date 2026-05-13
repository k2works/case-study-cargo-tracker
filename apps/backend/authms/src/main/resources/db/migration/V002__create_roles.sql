-- V002: roles テーブル作成
--
-- システムロール定義。初期データとして ADMIN / USER を投入する。

CREATE TABLE IF NOT EXISTS roles (
    id   VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO roles (id, name) VALUES
    ('role-admin', 'ADMIN'),
    ('role-user',  'USER');
