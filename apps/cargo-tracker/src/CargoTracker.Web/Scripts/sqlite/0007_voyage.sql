-- 航海スケジュールテーブル（SQLite 方言・US24・data-model IT3 実装状況）
-- DATE・TIMESTAMP は TEXT アフィニティで保持する（ADR-0003 二方言差異）。
CREATE TABLE voyage (
    id                         INTEGER PRIMARY KEY AUTOINCREMENT,
    voyage_number              TEXT    NOT NULL,
    vessel_name                TEXT    NOT NULL,
    carrier                    TEXT    NOT NULL,
    supported_cargo_types      TEXT    NOT NULL,
    created_at                 TEXT    NOT NULL,
    updated_at                 TEXT    NOT NULL,
    version                    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_voyage_voyage_number UNIQUE (voyage_number)
);

CREATE TABLE carrier_movement (
    id                            INTEGER PRIMARY KEY AUTOINCREMENT,
    voyage_id                     INTEGER NOT NULL REFERENCES voyage(id),
    departure_location_unlocode   TEXT    NOT NULL,
    arrival_location_unlocode     TEXT    NOT NULL,
    departure_date                TEXT    NOT NULL,
    arrival_date                  TEXT    NOT NULL,
    seq_number                    INTEGER NOT NULL,
    created_at                    TEXT    NOT NULL,
    updated_at                    TEXT    NOT NULL,
    CONSTRAINT uk_carrier_movement_voyage_seq UNIQUE (voyage_id, seq_number),
    CONSTRAINT ck_carrier_movement_seq_positive CHECK (seq_number >= 1)
);
