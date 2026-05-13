-- V004: Cargo Tracker 用ロールを追加
--
-- Role enum に対応するロールを roles テーブルに追加する。

INSERT INTO roles (id, name) VALUES
    ('role-sales',    'ROLE_SALES'),
    ('role-routing',  'ROLE_ROUTING'),
    ('role-handling', 'ROLE_HANDLING'),
    ('role-tracking', 'ROLE_TRACKING'),
    ('role-billing',  'ROLE_BILLING'),
    ('role-shipper',  'ROLE_SHIPPER');

-- 既存の ADMIN / USER を ROLE_ プレフィックス付きに更新
UPDATE roles SET name = 'ROLE_ADMIN' WHERE id = 'role-admin';
UPDATE roles SET name = 'ROLE_USER' WHERE id = 'role-user';
