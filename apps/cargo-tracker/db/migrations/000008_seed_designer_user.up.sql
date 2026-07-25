-- デモ用: 経路設計者（ROLE_ROUTE_DESIGNER）ユーザーを追加する。
-- パスワードは他のデモユーザーと同じ 'password'（bcrypt ハッシュ）。
INSERT INTO users (username, email, password, enabled) VALUES
    ('designer', 'designer@example.com', '$2a$10$K2a6OjigPmfUS3HBNGYWxOrDuDZHzkiNw8.C2dIWc70M52Gr6PBzO', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ROUTE_DESIGNER' FROM users WHERE username = 'designer'
ON CONFLICT DO NOTHING;
