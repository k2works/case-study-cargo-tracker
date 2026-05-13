-- V002: cargo_summary / cargo_leg Read Model（US04 貨物予約登録）
--
-- Cargo Aggregate（Axon Event Sourcing）の Read Model。
-- Event Store の CargoBookedEvent 等を CargoProjectionsEventHandler が購読し、
-- ここに INSERT/UPDATE する。
--
-- 設計準拠: docs/design/data-model.md の Booking Read Model セクション
-- shipper_id は既存 shipper.id (BIGINT) と FK 整合。data-model.md では VARCHAR(36) だが
-- 既存 IT1 実装との整合を優先（将来 ADR で同期検討）。

CREATE TABLE IF NOT EXISTS cargo_summary (
    booking_id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    shipper_id           BIGINT        NOT NULL,
    tracking_number      VARCHAR(20),
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    arrival_deadline     DATE          NOT NULL,
    cargo_type           VARCHAR(16)   NOT NULL,
    weight_kg            DECIMAL(12,2) NOT NULL,
    length_cm            INTEGER,
    width_cm             INTEGER,
    height_cm            INTEGER,
    quantity             INTEGER       NOT NULL,
    product_name         VARCHAR(200)  NOT NULL,
    hazard_imo_class     VARCHAR(20),
    hazard_un_number     VARCHAR(20),
    hazard_declaration   VARCHAR(1000),
    temperature_min_c    DECIMAL(5,2),
    temperature_max_c    DECIMAL(5,2),
    booking_status       VARCHAR(20)   NOT NULL,
    routing_status       VARCHAR(16)   NOT NULL,
    estimated_amount     DECIMAL(14,2),
    estimated_currency   VARCHAR(3),
    last_event_at        TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_cargo_summary_tracking UNIQUE (tracking_number),
    CONSTRAINT fk_cargo_summary_shipper FOREIGN KEY (shipper_id) REFERENCES shipper(id),
    CONSTRAINT chk_cargo_summary_cargo_type CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED')),
    CONSTRAINT chk_cargo_summary_booking_status CHECK (booking_status IN (
        'PRELIMINARY', 'ROUTING', 'ROUTE_PROPOSED', 'CONFIRMED',
        'TRACKING_ISSUED', 'IN_TRANSIT', 'DELIVERED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT chk_cargo_summary_routing_status CHECK (routing_status IN ('NOT_ROUTED', 'ROUTED', 'MISROUTED'))
);

CREATE INDEX IF NOT EXISTS idx_cargo_summary_shipper ON cargo_summary(shipper_id);
CREATE INDEX IF NOT EXISTS idx_cargo_summary_booking_status ON cargo_summary(booking_status);
CREATE INDEX IF NOT EXISTS idx_cargo_summary_routing_status ON cargo_summary(routing_status);

-- 経路（Leg）テーブル（IT3 以降の経路設計で投入）。
-- US04 時点では空で OK。スキーマは事前定義しておく。
CREATE TABLE IF NOT EXISTS cargo_leg (
    booking_id      VARCHAR(36) NOT NULL,
    leg_seq         INTEGER     NOT NULL,
    voyage_number   VARCHAR(20) NOT NULL,
    load_unlocode   VARCHAR(5)  NOT NULL,
    unload_unlocode VARCHAR(5)  NOT NULL,
    load_at         TIMESTAMP   NOT NULL,
    unload_at       TIMESTAMP   NOT NULL,
    PRIMARY KEY (booking_id, leg_seq),
    CONSTRAINT fk_cargo_leg_booking FOREIGN KEY (booking_id) REFERENCES cargo_summary(booking_id)
);

CREATE INDEX IF NOT EXISTS idx_cargo_leg_voyage ON cargo_leg(voyage_number);
