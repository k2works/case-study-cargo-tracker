-- 旅程区間テーブル（SQLite 方言・US11・data-model IT4 実装状況）
-- 確定経路（CargoItinerary）を Cargo に紐付ける際の各 Leg を seq_number 連鎖で保持する。
-- timestamp は TEXT アフィニティで保持する（ADR-0003 二方言差異）。
CREATE TABLE leg (
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    cargo_id                  INTEGER NOT NULL REFERENCES cargo(id),
    seq_number                INTEGER NOT NULL,
    voyage_number             TEXT    NOT NULL,
    load_location_unlocode    TEXT    NOT NULL,
    unload_location_unlocode  TEXT    NOT NULL,
    load_time                 TEXT    NOT NULL,
    unload_time               TEXT    NOT NULL,
    created_at                TEXT    NOT NULL,
    updated_at                TEXT    NOT NULL,
    CONSTRAINT uk_leg_cargo_seq UNIQUE (cargo_id, seq_number),
    CONSTRAINT ck_leg_seq_positive CHECK (seq_number >= 1)
);
