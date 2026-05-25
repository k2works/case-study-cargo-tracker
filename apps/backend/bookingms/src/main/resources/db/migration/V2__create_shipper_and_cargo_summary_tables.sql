-- 荷主（Shipper）Read Model（個人 / 法人。法人は契約番号と割引率を保持）
CREATE TABLE IF NOT EXISTS shipper (
    shipper_id      VARCHAR(36)  NOT NULL PRIMARY KEY,
    shipper_type    VARCHAR(16)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    address_line1   VARCHAR(200) NOT NULL,
    address_line2   VARCHAR(200),
    city            VARCHAR(100) NOT NULL,
    country_code    VARCHAR(2)   NOT NULL,
    postal_code     VARCHAR(20),
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(30)  NOT NULL,
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(4,3),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- 貨物予約サマリ（Cargo Aggregate の Read Model。US04 以降で列を拡張）
CREATE TABLE IF NOT EXISTS cargo_summary (
    booking_id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    shipper_id           VARCHAR(36)  NOT NULL,
    tracking_number      VARCHAR(25),
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    arrival_deadline     DATE         NOT NULL,
    cargo_type           VARCHAR(16)  NOT NULL,
    booking_status       VARCHAR(20)  NOT NULL DEFAULT 'PRELIMINARY',
    routing_status       VARCHAR(16)  NOT NULL DEFAULT 'NOT_ROUTED',
    last_event_at        TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cargo_summary_tracking_number
    ON cargo_summary (tracking_number);
CREATE INDEX IF NOT EXISTS idx_cargo_summary_shipper_id
    ON cargo_summary (shipper_id);
CREATE INDEX IF NOT EXISTS idx_cargo_summary_booking_status
    ON cargo_summary (booking_status);
