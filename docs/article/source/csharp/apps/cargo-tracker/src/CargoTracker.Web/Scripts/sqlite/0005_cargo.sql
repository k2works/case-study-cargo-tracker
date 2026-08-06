-- 貨物予約テーブル（SQLite 方言・US04・data-model IT2 実装状況）
-- decimal・DATE・timestamp は TEXT アフィニティで保持する（ADR-0003 二方言差異）。
CREATE TABLE cargo (
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id                TEXT    NOT NULL,
    shipper_id                INTEGER NOT NULL REFERENCES shipper(id),
    cargo_type                TEXT    NOT NULL,
    weight                    TEXT    NOT NULL,
    origin_unlocode           TEXT    NOT NULL,
    destination_unlocode      TEXT    NOT NULL,
    arrival_deadline          TEXT    NOT NULL,
    booking_status            TEXT    NOT NULL DEFAULT 'PRELIMINARY',
    dimension_length          TEXT,
    dimension_width           TEXT,
    dimension_height          TEXT,
    quantity                  INTEGER,
    description               TEXT,
    hazardous_class           TEXT,
    un_number                 TEXT,
    proper_shipping_name      TEXT,
    min_temperature           TEXT,
    max_temperature           TEXT,
    temperature_unit          TEXT,
    created_at                TEXT    NOT NULL,
    updated_at                TEXT    NOT NULL,
    version                   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_cargo_booking_id UNIQUE (booking_id),
    CONSTRAINT ck_cargo_quantity_positive CHECK (quantity IS NULL OR quantity >= 1)
);
