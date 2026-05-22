-- 初期ユーザーデータ（H2 インメモリ DB 用、パスワードはすべて "password" の BCrypt ハッシュ）
MERGE INTO users (username, password, role, failed_login_attempts)
KEY (username)
VALUES ('admin',    '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ADMIN',   0);

MERGE INTO users (username, password, role, failed_login_attempts)
KEY (username)
VALUES ('routing1', '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_ROUTING', 0);

MERGE INTO users (username, password, role, failed_login_attempts)
KEY (username)
VALUES ('sales1',   '$2b$10$BamHSxgZoUO/NjWyo8hUXe0htX.5Dj7712AG5yGtN3qrrPIIefFWO', 'ROLE_SALES',   0);
