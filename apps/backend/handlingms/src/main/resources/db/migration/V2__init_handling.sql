-- handlingms の実テーブル（IT7・US15・US16）。
--
-- 地点マスタは bookingms が正であり、同一内容の種データを配る（[ADR-014]）。
-- **内容をここで直さない。** ずれは LocationSeedReplicaTest が落とす。

CREATE TABLE location (
    id           BIGSERIAL PRIMARY KEY,
    unlocode     VARCHAR(5)   NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    country_code VARCHAR(2)   NOT NULL,
    -- 到着期限は目的地の暦で判断する。UTC で判断すると、時差の分だけ
    -- 受付が拒否される時間帯ができる。後から必須にすると既存行が読めなくなるため最初から NOT NULL
    time_zone    VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO location (unlocode, name, country_code, time_zone) VALUES
    ('JPTYO', 'Tokyo',        'JP', 'Asia/Tokyo'),
    ('JPYOK', 'Yokohama',     'JP', 'Asia/Tokyo'),
    ('JPOSA', 'Osaka',        'JP', 'Asia/Tokyo'),
    ('USLAX', 'Los Angeles',  'US', 'America/Los_Angeles'),
    ('USNYC', 'New York',     'US', 'America/New_York'),
    ('CNSHA', 'Shanghai',     'CN', 'Asia/Shanghai'),
    ('SGSIN', 'Singapore',    'SG', 'Asia/Singapore'),
    ('DEHAM', 'Hamburg',      'DE', 'Europe/Berlin'),
    ('NLRTM', 'Rotterdam',    'NL', 'Europe/Amsterdam'),
    ('AUMEL', 'Melbourne',    'AU', 'Australia/Melbourne');

-- 荷役作業の記録（[ADR-023]）。
--
-- **実際に起きた作業の記録である。**あとから「無かったこと」にはできない。
CREATE TABLE handling_activity (
    id                     BIGSERIAL PRIMARY KEY,
    -- 予約番号は論理参照（DB が分かれている）。荷役の記録はこれで他サービスと突き合わせる
    booking_id             VARCHAR(20)  NOT NULL,
    event_type             VARCHAR(30)  NOT NULL,
    event_completion_time  TIMESTAMP WITH TIME ZONE NOT NULL,
    location_unlocode      VARCHAR(5)   NOT NULL REFERENCES location (unlocode),
    -- 積込・荷降しでのみ埋まる。どの船に載せたか分からないと貨物を追えない
    voyage_number          VARCHAR(20),
    -- 誰が記録したか分からない記録は監査に使えない
    operator_name          VARCHAR(200) NOT NULL,
    -- 引取でのみ埋まる。通関ガード（US29・IT9）の代替（[ADR-023] 決定 4）
    consignee_confirmation VARCHAR(200),
    -- 予定と違う場所での作業だったか（[ADR-023] 決定 3）。
    -- **判定の結果を残す。**US28（IT10）で誤配を扱うときに、過去の作業を判定し直さない
    -- ためである。判定には旅程が要り、旅程は変わりうる
    off_route              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 1 つの貨物の荷役を時系列で読む（US15 の履歴）。
CREATE INDEX idx_handling_activity_booking ON handling_activity (booking_id, event_completion_time);
