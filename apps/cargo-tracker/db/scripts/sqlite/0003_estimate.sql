-- estimate / route_candidate（見積・US01）。SQLite 方言。
CREATE TABLE estimate (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    estimate_id          TEXT    NOT NULL UNIQUE,
    origin_unlocode      TEXT    NOT NULL,
    destination_unlocode TEXT    NOT NULL,
    arrival_deadline     TEXT    NOT NULL,
    cargo_type           TEXT    NOT NULL,
    weight_kg            NUMERIC NOT NULL,
    status               TEXT    NOT NULL DEFAULT 'CREATED',
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL
);

CREATE TABLE route_candidate (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    estimate_id    INTEGER NOT NULL REFERENCES estimate(id),
    voyage_number  TEXT    NOT NULL,
    transit_port   TEXT,
    transit_days   INTEGER NOT NULL,
    estimated_cost NUMERIC NOT NULL,
    rank           INTEGER NOT NULL DEFAULT 0
);
