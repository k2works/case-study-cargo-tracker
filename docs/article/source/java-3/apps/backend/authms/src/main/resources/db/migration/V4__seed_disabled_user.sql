-- 無効化されたアカウントの挙動（US31）を画面から確かめるための利用者。
-- ログイン画面の「動作確認用の利用者」一覧に載せている以上、実在しないと試せない。
--
-- V3 は既に適用済みの環境があるため編集しない。適用済みのマイグレーションを書き換えると
-- チェックサムが変わり、その環境の起動が止まる（実際に kind で発生した）。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('disabled01', 'disabled01@example.com', '退職済 太郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', FALSE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES' FROM users WHERE username = 'disabled01';
