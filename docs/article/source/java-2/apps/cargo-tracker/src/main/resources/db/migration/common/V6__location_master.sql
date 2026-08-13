-- 港マスタ（UN/LOCODE）。
--
-- **これは業務マスタであり、動作確認用データではない。** db/seed や db/demo ではなく
-- common に置く。carrier_movement は location への外部キーを持つため、
-- **このデータが無いと航海スケジュールを 1 件も登録できない**（IT3 計画時の突合で発覚）。
--
-- 網羅は目的ではない。US08（経路候補の算出）に足りる主要港に絞る。
-- 実運用では船会社の寄港地に合わせて追加する。

-- data-model.md が定める country_code と time_zone を追加する。
-- V1 で作られていなかった（テーブルはあるがカラムが揃っていない）。
--
-- **time_zone は「その港の現地時刻」を求めるために要る。** 到着期限の判定は
-- 日付単位で行うため（domain-model.md ビジネスルール 2-1）、TIMESTAMPTZ の時点を
-- どのタイムゾーンで日付に丸めるかで結果が変わる。
ALTER TABLE location ADD COLUMN country_code VARCHAR(2);
ALTER TABLE location ADD COLUMN time_zone    VARCHAR(50);

INSERT INTO location (unlocode, name, country_code, time_zone) VALUES
    -- 日本
    ('JPTYO', '東京',           'JP', 'Asia/Tokyo'),
    ('JPYOK', '横浜',           'JP', 'Asia/Tokyo'),
    ('JPOSA', '大阪',           'JP', 'Asia/Tokyo'),
    ('JPKIX', '関西',           'JP', 'Asia/Tokyo'),
    ('JPNGO', '名古屋',         'JP', 'Asia/Tokyo'),
    ('JPHKT', '博多',           'JP', 'Asia/Tokyo'),
    ('JPKOB', '神戸',           'JP', 'Asia/Tokyo'),
    -- アジア
    ('CNSHA', '上海',           'CN', 'Asia/Shanghai'),
    ('CNSZX', '深圳',           'CN', 'Asia/Shanghai'),
    ('HKHKG', '香港',           'HK', 'Asia/Hong_Kong'),
    ('SGSIN', 'シンガポール',   'SG', 'Asia/Singapore'),
    ('KRPUS', '釜山',           'KR', 'Asia/Seoul'),
    ('TWKHH', '高雄',           'TW', 'Asia/Taipei'),
    ('THBKK', 'バンコク',       'TH', 'Asia/Bangkok'),
    ('MYPKG', 'ポートケラン',   'MY', 'Asia/Kuala_Lumpur'),
    ('INNSA', 'ジャワハルラール・ネルー', 'IN', 'Asia/Kolkata'),
    ('AEJEA', 'ジェベルアリ',   'AE', 'Asia/Dubai'),
    -- 北米
    ('USLAX', 'ロサンゼルス',   'US', 'America/Los_Angeles'),
    ('USLGB', 'ロングビーチ',   'US', 'America/Los_Angeles'),
    ('USSEA', 'シアトル',       'US', 'America/Los_Angeles'),
    ('USOAK', 'オークランド',   'US', 'America/Los_Angeles'),
    ('USNYC', 'ニューヨーク',   'US', 'America/New_York'),
    ('USSAV', 'サバンナ',       'US', 'America/New_York'),
    ('USHOU', 'ヒューストン',   'US', 'America/Chicago'),
    ('CAVAN', 'バンクーバー',   'CA', 'America/Vancouver'),
    ('MXZLO', 'マンサニヨ',     'MX', 'America/Mexico_City'),
    -- 欧州
    ('NLRTM', 'ロッテルダム',   'NL', 'Europe/Amsterdam'),
    ('DEHAM', 'ハンブルク',     'DE', 'Europe/Berlin'),
    ('BEANR', 'アントワープ',   'BE', 'Europe/Brussels'),
    ('GBFXT', 'フェリクストウ', 'GB', 'Europe/London'),
    ('GBSOU', 'サウサンプトン', 'GB', 'Europe/London'),
    ('FRLEH', 'ルアーブル',     'FR', 'Europe/Paris'),
    ('ESVLC', 'バレンシア',     'ES', 'Europe/Madrid'),
    ('ITGOA', 'ジェノヴァ',     'IT', 'Europe/Rome'),
    -- オセアニア・南米
    ('AUSYD', 'シドニー',       'AU', 'Australia/Sydney'),
    ('AUMEL', 'メルボルン',     'AU', 'Australia/Melbourne'),
    ('BRSSZ', 'サントス',       'BR', 'America/Sao_Paulo'),
    ('CLVAP', 'バルパライソ',   'CL', 'America/Santiago');
