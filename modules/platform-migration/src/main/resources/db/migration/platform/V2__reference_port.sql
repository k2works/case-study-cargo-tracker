-- 港湾（読み取り専用の参照データ）。維持する画面・ストーリーは無い。
-- R-09：実在する UN/LOCODE を投入する（出典：UNECE UN/LOCODE）。部分集合のみ。

CREATE TABLE reference.port (
  port_code    platform.port_code PRIMARY KEY,
  country_code char(2) NOT NULL,
  name         text    NOT NULL,   -- 日本語名
  name_en      text    NOT NULL,
  CONSTRAINT port_country_matches_code CHECK (country_code = left(port_code, 2))
);

INSERT INTO reference.port (port_code, country_code, name, name_en) VALUES
  ('JPTYO', 'JP', '東京',           'Tokyo'),
  ('SGSIN', 'SG', 'シンガポール',   'Singapore'),
  ('NLRTM', 'NL', 'ロッテルダム',   'Rotterdam'),
  ('CNSHA', 'CN', '上海',           'Shanghai'),
  ('USLAX', 'US', 'ロサンゼルス',   'Los Angeles'),
  ('KRPUS', 'KR', '釜山',           'Busan'),
  ('DEHAM', 'DE', 'ハンブルク',     'Hamburg');
