-- leg（輸送区間・US09-13 経路確定/旅程永続化）。PostgreSQL 方言。
-- cargo 集約に属する旅程の各区間を保持する。集約ルート cargo 経由でのみ更新される（ADR-0001）。
CREATE TABLE leg (
    id                       BIGSERIAL   PRIMARY KEY,
    cargo_id                 BIGINT      NOT NULL REFERENCES cargo(id),
    voyage_number            VARCHAR(20) NOT NULL,
    load_location_unlocode   VARCHAR(5)  NOT NULL,
    unload_location_unlocode VARCHAR(5)  NOT NULL,
    load_time                TEXT   NOT NULL,
    unload_time              TEXT   NOT NULL,
    seq_number               INTEGER     NOT NULL,
    created_at               TEXT   NOT NULL DEFAULT (now())::text,
    updated_at               TEXT   NOT NULL DEFAULT (now())::text
);

CREATE INDEX idx_leg_cargo_id ON leg(cargo_id);
