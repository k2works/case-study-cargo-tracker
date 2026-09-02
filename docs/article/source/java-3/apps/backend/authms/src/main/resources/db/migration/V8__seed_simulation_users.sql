-- 業務シミュレーション専用の利用者（[ADR-030] 決定 2・IT15）。
--
-- **実業務の利用者を借りない。** IT14 はシミュレーションを sales01 として動かして
-- いたが、「シミュレーション由来として荷主を登録できる」名簿にも sales01 が載るため、
-- 実の営業担当者が自分の登録を由来つきにできた——精算の締めから消える操作である。
--
-- **ロールごとに 1 人ずつ置く。** 1 人に全ロールを与えると、本番には存在しない権限の
-- 持ち主ができ、工程が誤って別ロールを要求していても 403 にならず気づけない。
-- 権限は実利用者と同じであり、シミュレーションだけが通る経路は作らない。
--
-- パスワードはすべて "password"（BCrypt ハッシュ）。本番環境ではこのマイグレーションを
-- 適用せず、シミュレーション自体を無効にする（SimulationSafetyConfig）。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('sim-sales01',      'sim-sales01@simulation.example.com',      'シミュレーション営業',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('sim-routing01',    'sim-routing01@simulation.example.com',    'シミュレーション経路設計',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('sim-handler01',    'sim-handler01@simulation.example.com',    'シミュレーション荷役',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('sim-tracker01',    'sim-tracker01@simulation.example.com',    'シミュレーション追跡',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('sim-accountant01', 'sim-accountant01@simulation.example.com', 'シミュレーション経理',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES'      FROM users WHERE username = 'sim-sales01'
UNION ALL SELECT id, 'ROLE_ROUTING'    FROM users WHERE username = 'sim-routing01'
UNION ALL SELECT id, 'ROLE_HANDLER'    FROM users WHERE username = 'sim-handler01'
UNION ALL SELECT id, 'ROLE_TRACKER'    FROM users WHERE username = 'sim-tracker01'
UNION ALL SELECT id, 'ROLE_ACCOUNTANT' FROM users WHERE username = 'sim-accountant01';
