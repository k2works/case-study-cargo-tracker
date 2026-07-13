-- 旅程区間テーブル（PostgreSQL 方言・US11・data-model IT4 実装状況）
-- 確定経路（CargoItinerary）を Cargo に紐付ける際の各 Leg を seq_number 連鎖で保持する。
CREATE TABLE leg (
    id                        BIGSERIAL   PRIMARY KEY,
    cargo_id                  BIGINT      NOT NULL REFERENCES cargo(id),
    seq_number                INTEGER     NOT NULL,
    voyage_number             VARCHAR(20) NOT NULL,
    load_location_unlocode    VARCHAR(5)  NOT NULL,
    unload_location_unlocode  VARCHAR(5)  NOT NULL,
    load_time                 TIMESTAMP   NOT NULL,
    unload_time               TIMESTAMP   NOT NULL,
    created_at                TIMESTAMP   NOT NULL,
    updated_at                TIMESTAMP   NOT NULL,
    CONSTRAINT uk_leg_cargo_seq UNIQUE (cargo_id, seq_number),
    CONSTRAINT ck_leg_seq_positive CHECK (seq_number >= 1)
);
