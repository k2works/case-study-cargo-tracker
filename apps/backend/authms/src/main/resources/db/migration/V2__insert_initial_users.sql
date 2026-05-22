-- 初期ユーザーデータ（パスワードはすべて "password" の BCrypt ハッシュ）
INSERT INTO users (username, password, role)
SELECT 'admin', '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, password, role)
SELECT 'routing1', '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ROUTING'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'routing1');

INSERT INTO users (username, password, role)
SELECT 'sales1', '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_SALES'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'sales1');
