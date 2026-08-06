-- 見積・ルート候補テーブル（SQLite 方言・US01・data-model）
-- UUID・DATE・decimal 列は TEXT アフィニティで保持する（型アフィニティによる
-- Dapper マテリアライザキャッシュ破綻を避けるため・ADR-0003 二方言差異）。
CREATE TABLE estimate (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    estimate_id          TEXT    NOT NULL,
    origin_unlocode      TEXT    NOT NULL,
    destination_unlocode TEXT    NOT NULL,
    arrival_deadline     TEXT    NOT NULL,
    cargo_type           TEXT    NOT NULL,
    weight_kg            TEXT    NOT NULL,
    status               TEXT    NOT NULL DEFAULT 'CREATED',
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    version              INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_estimate_id UNIQUE (estimate_id)
);

CREATE TABLE route_candidate (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    estimate_id    INTEGER NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number  TEXT    NOT NULL,
    transit_port   TEXT,
    transit_days   INTEGER NOT NULL,
    estimated_cost TEXT    NOT NULL,
    rank           INTEGER NOT NULL DEFAULT 0
);
