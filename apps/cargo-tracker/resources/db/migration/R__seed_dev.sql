-- 開発・テスト用のシードデータ（繰り返し実行可能）
-- Flyway の Repeatable マイグレーション（R__ プレフィックス）。チェックサム変更時に再実行される。
-- 本番環境では適用しない（起動時にプロファイルで location を切り替える。IT3 の CI 構築時に対応）

MERGE INTO location (unlocode, name, country_code, time_zone) KEY (unlocode) VALUES
    ('JPTYO', 'Tokyo',       'JP', 'Asia/Tokyo'),
    ('JPOSA', 'Osaka',       'JP', 'Asia/Tokyo'),
    ('USLAX', 'Los Angeles', 'US', 'America/Los_Angeles'),
    ('DEHAM', 'Hamburg',     'DE', 'Europe/Berlin'),
    ('CNSHA', 'Shanghai',    'CN', 'Asia/Shanghai'),
    ('HKHKG', 'Hong Kong',   'HK', 'Asia/Hong_Kong'),
    ('SGSIN', 'Singapore',   'SG', 'Asia/Singapore');
