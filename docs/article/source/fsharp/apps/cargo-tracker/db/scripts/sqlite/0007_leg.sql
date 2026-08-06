-- leg（輸送区間・US09-13 経路確定/旅程永続化）。SQLite 方言。
-- cargo 集約に属する旅程の各区間を保持する。集約ルート cargo 経由でのみ更新される（ADR-0001）。
CREATE TABLE leg (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    cargo_id                 INTEGER NOT NULL REFERENCES cargo(id),
    voyage_number            TEXT    NOT NULL,
    load_location_unlocode   TEXT    NOT NULL,
    unload_location_unlocode TEXT    NOT NULL,
    load_time                TEXT    NOT NULL,
    unload_time              TEXT    NOT NULL,
    seq_number               INTEGER NOT NULL,
    created_at               TEXT    NOT NULL,
    updated_at               TEXT    NOT NULL
);

CREATE INDEX idx_leg_cargo_id ON leg(cargo_id);
