-- 動作確認用の利用者（ADR-0004）。
--
-- **この場所は既定では読まれない。** cargo-tracker.demo-users=true を渡した環境だけが
-- classpath:db/seed を Flyway の場所に足す（DemoUserSeedConfiguration）。既定を安全側に
-- 倒しているのは、パスワードが分かっている利用者を業務環境へ持ち込む経路を作らないため。
--
-- 版付き（V*）ではなく反復（R__）にしてある。版付きにすると、あとから db/migration に
-- 新しい版を足したときに順序が前後し、Flyway が out-of-order で止まる。反復なら常に
-- 版付きの後に走り、内容を変えたときだけ再実行される。
--
-- パスワードは全員共通で secret1234（BCrypt cost 10）。画面にもそう表示する。
-- 一覧は frontend の src/features/auth/demoAccounts.ts と一致させること。
-- 食い違いは DemoAccountsMatchSeedTest が赤にする。

INSERT INTO users (username, password_hash, display_name, shipper_id, enabled, failed_attempts, created_at, updated_at)
VALUES
    ('sales01',      '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '営業 太郎',   NULL, TRUE,  0, now(), now()),
    ('routing01',    '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '経路 花子',   NULL, TRUE,  0, now(), now()),
    ('tracker01',    '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '追跡 次郎',   NULL, TRUE,  0, now(), now()),
    ('handler01',    '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '荷役 三郎',   NULL, TRUE,  0, now(), now()),
    ('accountant01', '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '経理 四郎',   NULL, TRUE,  0, now(), now()),
    ('shipper01',    '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '荷主 五郎',   NULL, TRUE,  0, now(), now()),
    ('admin01',      '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '管理 六郎',   NULL, TRUE,  0, now(), now()),
    ('disabled01',   '$2a$10$Pap5MEtrfTd55wwSny.X/eSSC96w81dIblU.baI9Xq2XlwMPStTBa', '無効 七郎',   NULL, FALSE, 0, now(), now())
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (username, role)
VALUES
    ('sales01',      'ROLE_SALES'),
    ('routing01',    'ROLE_ROUTING'),
    ('tracker01',    'ROLE_TRACKER'),
    ('handler01',    'ROLE_HANDLER'),
    ('accountant01', 'ROLE_ACCOUNTANT'),
    ('shipper01',    'ROLE_SHIPPER'),
    ('admin01',      'ROLE_ADMIN'),
    ('disabled01',   'ROLE_SALES')
ON CONFLICT (username, role) DO NOTHING;
