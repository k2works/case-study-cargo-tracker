-- 場所マスタ（繰り返し実行可能）
-- Flyway の Repeatable マイグレーション（R__ プレフィックス）。チェックサム変更時に再実行される。
--
-- **`location` は開発用のシードではなくマスタデータである**。`cargo` の出発地・目的地が
-- 外部キーで参照するため、本番でも必要になる。ファイル名の `seed_dev` は実態と合っていないが、
-- 改名すると Flyway が別のマイグレーションとして扱うため、IT5 のスキーマ整理でまとめて行う。
--
-- **DB 固有の upsert 構文を使わない**（IT4 で是正）。当初は `MERGE INTO ... KEY (...)` を
-- 使っていたが、これは H2 固有であり PostgreSQL では構文エラーになる。
-- **本ファイルは本番でも適用されるため、そのままでは本番でアプリケーションが起動できなかった。**
--
-- 代替として `ON CONFLICT DO NOTHING`（PostgreSQL 固有）も試したが、H2 は解釈しない。
-- 両方で動くのは標準 SQL の `INSERT ... SELECT ... WHERE NOT EXISTS` である。
-- **「どちらか一方でしか動かない構文」を書いた時点で、片方の環境は検証されていない。**

INSERT INTO location (unlocode, name, country_code, time_zone)
SELECT seed.unlocode, seed.name, seed.country_code, seed.time_zone
FROM (VALUES
    ('JPTYO', 'Tokyo',       'JP', 'Asia/Tokyo'),
    ('JPOSA', 'Osaka',       'JP', 'Asia/Tokyo'),
    ('USLAX', 'Los Angeles', 'US', 'America/Los_Angeles'),
    ('DEHAM', 'Hamburg',     'DE', 'Europe/Berlin'),
    ('CNSHA', 'Shanghai',    'CN', 'Asia/Shanghai'),
    ('HKHKG', 'Hong Kong',   'HK', 'Asia/Hong_Kong'),
    ('SGSIN', 'Singapore',   'SG', 'Asia/Singapore')
) AS seed (unlocode, name, country_code, time_zone)
WHERE NOT EXISTS (
    SELECT 1 FROM location existing WHERE existing.unlocode = seed.unlocode
);
