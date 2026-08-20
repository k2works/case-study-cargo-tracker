-- 地点と貨物（IT2 / US04・US05）。適用済みの V1・V2 は編集しない。
-- 編集すると checksum が変わり、既にデプロイ済みの環境が起動できなくなる。

-- 地点マスタ（ADR-010）。
-- 主キーはサロゲート、業務上の識別子 UN/LOCODE には UNIQUE を置く。
-- UN/LOCODE は統廃合・改称で改訂されうるため、主キーにすると全参照を追うことになる。
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

-- 予約番号（ADR-011）。5 サービスが論理参照するキーであり、値そのものが契約になる。
-- 組み立てはここに置く。アプリ側で文字列を作ると、別の経路（移行・運用スクリプト）が
-- 違う形式を発行できてしまう。
CREATE SEQUENCE booking_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE cargo (
    id                        BIGSERIAL PRIMARY KEY,
    -- DEFAULT は制約より前に置く。順序を入れ替えると H2 が解釈できず、
    -- 本番（PostgreSQL）だけが緑になる
    booking_id                VARCHAR(20)
        DEFAULT ('BKG-' || TO_CHAR(CURRENT_DATE, 'YYYY')
                 || LPAD(CAST(NEXTVAL('booking_id_seq') AS VARCHAR), 6, '0'))
        NOT NULL UNIQUE,
    shipper_id                BIGINT      NOT NULL REFERENCES shipper (id),
    booking_status            VARCHAR(30) NOT NULL,
    -- 「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）。
    -- 後から NOT NULL を足すと、IT2 で入った行が読めなくなる
    transport_status          VARCHAR(30) NOT NULL,
    routing_status            VARCHAR(30) NOT NULL,
    cargo_type                VARCHAR(20) NOT NULL,
    weight_kg                 NUMERIC(10,3) NOT NULL,
    quantity                  INTEGER,
    description               VARCHAR(500),
    length_cm                 NUMERIC(8,2),
    width_cm                  NUMERIC(8,2),
    height_cm                 NUMERIC(8,2),
    spec_origin_unlocode      VARCHAR(5)  NOT NULL REFERENCES location (unlocode),
    spec_destination_unlocode VARCHAR(5)  NOT NULL REFERENCES location (unlocode),
    spec_arrival_deadline     DATE        NOT NULL,
    spec_departure_date       DATE,
    -- 料金は計算結果であり、IT2 では算出できない（US18・IT11）。
    -- 0 で埋めると未算出と無料が区別できず、算出漏れが無料の予約として通る
    booking_amount_value      INTEGER,
    booking_amount_currency   VARCHAR(3),
    hazardous_class           VARCHAR(20),
    un_number                 VARCHAR(10),
    proper_shipping_name      VARCHAR(200),
    temp_min                  NUMERIC(5,2),
    temp_max                  NUMERIC(5,2),
    temp_unit                 VARCHAR(10),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 一覧は新しい順に出す。営業の使い方は「登録した直後に一覧へ戻って入ったか確かめる」であり、
-- 登録順だと今入れた 1 件が常に最下部に沈む
CREATE INDEX idx_cargo_shipper ON cargo (shipper_id);
CREATE INDEX idx_cargo_booking_status ON cargo (booking_status);
