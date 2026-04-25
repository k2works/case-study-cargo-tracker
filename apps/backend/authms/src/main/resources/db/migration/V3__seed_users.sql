-- ロールマスタデータ
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
INSERT INTO roles (name) VALUES ('ROLE_SALES');
INSERT INTO roles (name) VALUES ('ROLE_ROUTING');
INSERT INTO roles (name) VALUES ('ROLE_HANDLING');
INSERT INTO roles (name) VALUES ('ROLE_TRACKING');
INSERT INTO roles (name) VALUES ('ROLE_BILLING');
INSERT INTO roles (name) VALUES ('ROLE_SHIPPER');

-- 初期ユーザー（パスワードは BCrypt ハッシュ: "password"）
-- BCrypt hash of "password": $2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6
INSERT INTO users (username, email, password, enabled) VALUES
    ('admin',    'admin@example.com',    '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('sales',    'sales@example.com',    '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('routing',  'routing@example.com',  '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('handler',  'handler@example.com',  '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('tracker',  'tracker@example.com',  '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('billing',  'billing@example.com',  '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('shipper',  'shipper@example.com',  '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE);

-- ユーザーロール割り当て
INSERT INTO user_roles (user_id, role_id) VALUES
    ((SELECT id FROM users WHERE username = 'admin'),   (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')),
    ((SELECT id FROM users WHERE username = 'sales'),    (SELECT id FROM roles WHERE name = 'ROLE_SALES')),
    ((SELECT id FROM users WHERE username = 'routing'),  (SELECT id FROM roles WHERE name = 'ROLE_ROUTING')),
    ((SELECT id FROM users WHERE username = 'handler'),  (SELECT id FROM roles WHERE name = 'ROLE_HANDLING')),
    ((SELECT id FROM users WHERE username = 'tracker'),  (SELECT id FROM roles WHERE name = 'ROLE_TRACKING')),
    ((SELECT id FROM users WHERE username = 'billing'),  (SELECT id FROM roles WHERE name = 'ROLE_BILLING')),
    ((SELECT id FROM users WHERE username = 'shipper'),  (SELECT id FROM roles WHERE name = 'ROLE_SHIPPER'));
