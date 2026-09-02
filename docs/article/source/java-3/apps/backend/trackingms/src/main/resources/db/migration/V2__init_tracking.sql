-- trackingms の実テーブル（IT6・US14）。
--
-- 地点マスタは bookingms が正であり、同一内容の種データを配る（ADR-014）。
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

-- 追跡（US14）。
--
-- 集約ルートは TrackingActivity。IT6 で作るのは**追跡の開始まで**であり、
-- 荷役イベント（tracking_handling_event）と例外（tracking_exception_event）は
-- US15 以降で足す。**縮小実装であることを明記する**——書かないと実装漏れと読まれる。
CREATE TABLE tracking_activity (
    id               BIGSERIAL PRIMARY KEY,
    -- 照会の入口になる業務キー。bookingms が採番した番号を受け取る（ADR-021 / ADR-022）
    tracking_number  VARCHAR(20) NOT NULL UNIQUE,
    -- Booking Context への論理参照。DB が分かれているため FK は張らない
    booking_id       VARCHAR(20) NOT NULL,
    -- 「まだ受け取っていない」は空欄ではなく意味のある状態（ADR-009）
    transport_status VARCHAR(30) NOT NULL,
    origin_unlocode      VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    destination_unlocode VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    arrival_deadline     DATE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_activity_booking ON tracking_activity (booking_id);
