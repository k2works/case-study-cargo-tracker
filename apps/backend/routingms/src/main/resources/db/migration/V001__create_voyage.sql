-- V001: voyage / carrier_movement / voyage_accepted_cargo_type / location_master 作成（US24）
--
-- 経路設計コンテキストの Read Model。data-model.md の routing_read_db ER 図に準拠。

CREATE TABLE IF NOT EXISTS voyage (
    voyage_number        VARCHAR(20)   NOT NULL PRIMARY KEY,
    carrier_code         VARCHAR(10)   NOT NULL,
    carrier_name         VARCHAR(200)  NOT NULL,
    ship_name            VARCHAR(200)  NOT NULL,
    departure_date       TIMESTAMP     NOT NULL,
    arrival_date         TIMESTAMP     NOT NULL,
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    registered_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_voyage_status CHECK (status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED', 'CANCELLED')),
    CONSTRAINT chk_voyage_date_order CHECK (arrival_date > departure_date)
);

CREATE INDEX IF NOT EXISTS idx_voyage_route_date ON voyage(origin_unlocode, destination_unlocode, departure_date);

-- 寄港地（CarrierMovement）。voyage 1 件あたり 1 件以上の movement を持つ。
CREATE TABLE IF NOT EXISTS carrier_movement (
    voyage_number      VARCHAR(20) NOT NULL,
    movement_seq       INTEGER     NOT NULL,
    departure_unlocode VARCHAR(5)  NOT NULL,
    arrival_unlocode   VARCHAR(5)  NOT NULL,
    departure_time     TIMESTAMP   NOT NULL,
    arrival_time       TIMESTAMP   NOT NULL,
    PRIMARY KEY (voyage_number, movement_seq),
    CONSTRAINT fk_carrier_movement_voyage FOREIGN KEY (voyage_number) REFERENCES voyage(voyage_number),
    CONSTRAINT chk_carrier_movement_date_order CHECK (arrival_time > departure_time)
);

-- 対応貨物種別。voyage 1 件あたり 0 件以上。
CREATE TABLE IF NOT EXISTS voyage_accepted_cargo_type (
    voyage_number VARCHAR(20) NOT NULL,
    cargo_type    VARCHAR(16) NOT NULL,
    PRIMARY KEY (voyage_number, cargo_type),
    CONSTRAINT fk_voyage_accepted_cargo_type_voyage FOREIGN KEY (voyage_number) REFERENCES voyage(voyage_number),
    CONSTRAINT chk_voyage_accepted_cargo_type CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED'))
);

CREATE INDEX IF NOT EXISTS idx_voyage_accepted_cargo_type ON voyage_accepted_cargo_type(cargo_type);

-- UN/LOCODE マスタ。routingms が集中管理する（他コンテキストは Location VO で参照）。
CREATE TABLE IF NOT EXISTS location_master (
    unlocode     VARCHAR(5)    NOT NULL PRIMARY KEY,
    port_name    VARCHAR(200)  NOT NULL,
    country_code VARCHAR(2)    NOT NULL,
    latitude     DECIMAL(8,5),
    longitude    DECIMAL(8,5),
    active       BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_location_master_country ON location_master(country_code);

-- 初期マスタデータ（テスト・E2E 用）
INSERT INTO location_master (unlocode, port_name, country_code, latitude, longitude) VALUES
    ('JPYOK', 'Yokohama', 'JP', 35.45000, 139.65000),
    ('JPTYO', 'Tokyo',    'JP', 35.65000, 139.77000),
    ('USLAX', 'Los Angeles', 'US', 33.74000, -118.27000),
    ('USNYC', 'New York', 'US', 40.69000, -74.05000),
    ('TWKHH', 'Kaohsiung', 'TW', 22.62000, 120.30000);
