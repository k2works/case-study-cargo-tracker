-- 開発・検証用の初期利用者。パスワードはすべて "password"（BCrypt ハッシュ）。
-- 本番環境ではこのマイグレーションを適用せず、正規の利用者登録手順を用いる
-- （運用手順書の利用者管理を参照）。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('sales01',      'sales01@example.com',      '山田太郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('routing01',    'routing01@example.com',    '田中次郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('handler01',    'handler01@example.com',    '鈴木一郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('tracker01',    'tracker01@example.com',    '佐藤花子',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('accountant01', 'accountant01@example.com', '高橋美咲',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('shipper01',    'shipper01@example.com',    '伊藤商事',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    -- 無効化されたアカウントの挙動（US31）を画面から確かめるための利用者。
    -- 実在しないと「ログインできないこと」を試せない
    ('disabled01',   'disabled01@example.com',   '退職済 太郎',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', FALSE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES'      FROM users WHERE username = 'sales01'
UNION ALL SELECT id, 'ROLE_ROUTING'    FROM users WHERE username = 'routing01'
UNION ALL SELECT id, 'ROLE_HANDLER'    FROM users WHERE username = 'handler01'
UNION ALL SELECT id, 'ROLE_TRACKER'    FROM users WHERE username = 'tracker01'
UNION ALL SELECT id, 'ROLE_ACCOUNTANT' FROM users WHERE username = 'accountant01'
UNION ALL SELECT id, 'ROLE_SHIPPER'    FROM users WHERE username = 'shipper01'
UNION ALL SELECT id, 'ROLE_SALES'      FROM users WHERE username = 'disabled01';
