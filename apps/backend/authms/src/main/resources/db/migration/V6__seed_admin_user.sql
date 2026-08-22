-- システム管理者（US32）。
--
-- **いないと、ロックされた利用者を誰も助けられない。** ロックされた本人には理由が
-- 表示されないため（US31）、自分では状況すら確かめられない。管理者が実在しないと、
-- 15 分待つ以外の手段が無くなる。
--
-- ログイン画面の「動作確認用の利用者」一覧に載せている以上、実在しないと試せない。
--
-- V3・V4 は既に適用済みの環境があるため編集しない。適用済みのマイグレーションを
-- 書き換えるとチェックサムが変わり、その環境の起動が止まる。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('admin01', 'admin01@example.com', '管理 太郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin01';
