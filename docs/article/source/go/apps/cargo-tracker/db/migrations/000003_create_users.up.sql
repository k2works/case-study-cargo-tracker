-- Shared Domain（サポート領域）: 認証・認可用テーブル
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(200) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- シードユーザー（パスワードはすべて 'password'、bcrypt ハッシュ）
INSERT INTO users (username, email, password, enabled) VALUES
    ('admin', 'admin@example.com', '$2a$10$K2a6OjigPmfUS3HBNGYWxOrDuDZHzkiNw8.C2dIWc70M52Gr6PBzO', TRUE),
    ('sales', 'sales@example.com', '$2a$10$K2a6OjigPmfUS3HBNGYWxOrDuDZHzkiNw8.C2dIWc70M52Gr6PBzO', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES' FROM users WHERE username = 'sales';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SHIPPER' FROM users WHERE username = 'sales';
