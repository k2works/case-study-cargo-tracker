-- 初期ユーザーデータ（パスワードはすべて "password" の BCrypt ハッシュ）
INSERT INTO users (username, password, role) VALUES
    ('admin',    '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ADMIN'),
    ('routing1', '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ROUTING'),
    ('sales1',   '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_SALES')
ON CONFLICT (username) DO NOTHING;
