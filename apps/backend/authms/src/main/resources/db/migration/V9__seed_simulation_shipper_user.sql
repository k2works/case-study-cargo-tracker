-- お知らせを確かめるための利用者（US39）。
--
-- **シミュレーションが作った貨物のお知らせは、誰かの画面に出て初めて確かめられる。**
-- 荷主に紐付いていない知らせは届け先が無く、実装が正しくても何も起きない。
--
-- sim-shipper01 は、実行のたびに **その回の荷主へ紐付け直される**
-- （ScenarioStep.LINK_SHIPPER_USER）。したがって見えるのはいつも最後の実行の貨物である
-- ——確かめたいのは「いま流したものが届くか」であり、過去の実行の一覧ではない。
--
-- sim-admin01 は、その紐付けを行う。**実業務と同じ管理者の操作**（US33）であり、
-- シミュレーション専用の経路は作らない。権限は実の管理者と同じで、
-- ロールを 1 つだけ持つ（V8 と同じ方針）。
--
-- パスワードはすべて "password"（BCrypt ハッシュ）。本番環境ではこのマイグレーションを
-- 適用せず、シミュレーション自体を無効にする（SimulationSafetyConfig）。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('sim-shipper01', 'sim-shipper01@simulation.example.com', 'シミュレーション荷主',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('sim-admin01',   'sim-admin01@simulation.example.com',   'シミュレーション管理者',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SHIPPER' FROM users WHERE username = 'sim-shipper01'
UNION ALL SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'sim-admin01';
