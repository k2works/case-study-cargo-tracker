-- voyage / carrier_movement（航海スケジュール・US24/US25/US07/US08）。SQLite 方言。
-- voyage は船名・運送会社・対応貨物種別を保持する（US24・ADR: data-model 拡張）。
CREATE TABLE voyage (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    voyage_number         TEXT    NOT NULL UNIQUE,
    vessel_name           TEXT    NOT NULL,
    carrier_name          TEXT    NOT NULL,
    supported_cargo_types TEXT    NOT NULL,  -- GENERAL,HAZARDOUS,REFRIGERATED のカンマ区切り
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL,
    version               INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE carrier_movement (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    voyage_id                   INTEGER NOT NULL REFERENCES voyage(id),
    departure_location_unlocode TEXT    NOT NULL,
    arrival_location_unlocode   TEXT    NOT NULL,
    departure_date              TEXT    NOT NULL,
    arrival_date                TEXT    NOT NULL,
    seq_number                  INTEGER NOT NULL,
    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL
);
