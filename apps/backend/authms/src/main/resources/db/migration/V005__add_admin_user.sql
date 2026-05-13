-- V005: テスト用初期ユーザーを追加
--
-- 各ロールの初期ユーザーを投入する（パスワード: "password"）。
-- テスト・開発環境での動作確認用。
-- BCrypt hash of "password": $2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6

INSERT INTO users (id, username, email, password, enabled) VALUES
    ('user-admin',   'admin',   'admin@example.com',   '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE),
    ('user-shipper', 'shipper', 'shipper@example.com', '$2a$10$M5slaMxWj0BWoWXgA4TRs.DoF9rPSbIPwLsopM3kNCPPt.W.CDJK6', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT 'user-admin', id FROM roles WHERE name = 'ROLE_ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT 'user-shipper', id FROM roles WHERE name = 'ROLE_SHIPPER';
