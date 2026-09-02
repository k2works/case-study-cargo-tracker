-- routingms の実スキーマ（data-model.md の routing_db / IT3 US24）。
-- 適用済みの V1 は編集しない。編集すると checksum が変わり、既にデプロイ済みの
-- 環境が起動できなくなる。

-- 地点マスタ。bookingms の複製である（ADR-010 / ADR-014）。
-- サービス間で JOIN しないため各サービスが自分の DB に持つ。内容は正（bookingms）と
-- 同一で、ずれは LocationSeedReplicaTest が落とす。形も揃える（country_code / time_zone は NOT NULL）。
CREATE TABLE location (
    id           BIGSERIAL PRIMARY KEY,
    unlocode     VARCHAR(5)   NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    country_code VARCHAR(2)   NOT NULL,
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

CREATE TABLE voyage (
    id                    BIGSERIAL PRIMARY KEY,
    voyage_number         VARCHAR(20)  NOT NULL UNIQUE,
    -- どの船かが分からないと、荷役の現場と問い合わせ窓口が貨物を追えない。
    -- 後から必須にすると、無かった期間に入った航海が読めなくなる
    vessel_name           VARCHAR(100) NOT NULL,
    carrier_name          VARCHAR(100) NOT NULL,
    -- 対応できる貨物種別（カンマ区切り）。危険物・冷凍は運べる船が限られる。
    -- 値は 3 つで検索条件としてしか使わないため別テーブルにしない。
    -- 空文字は許さない（何も運べない航海は登録の誤りであり、検索から静かに消える）
    supported_cargo_types VARCHAR(100) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_voyage_supported_cargo_types CHECK (supported_cargo_types <> '')
);

CREATE TABLE carrier_movement (
    id                         BIGSERIAL PRIMARY KEY,
    voyage_id                  BIGINT      NOT NULL REFERENCES voyage (id) ON DELETE CASCADE,
    departure_location_unlocode VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    arrival_location_unlocode   VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    departure_date             TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_date               TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 寄港の順序。順序が失われると、同じ港の集合でも別の航海になる
    seq_number                 INTEGER     NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_carrier_movement_seq UNIQUE (voyage_id, seq_number)
);

CREATE INDEX idx_carrier_movement_voyage ON carrier_movement (voyage_id, seq_number);
CREATE INDEX idx_carrier_movement_departure ON carrier_movement (departure_location_unlocode, departure_date);
