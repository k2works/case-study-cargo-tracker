-- Booking Context: 確定経路（CargoItinerary）の輸送区間テーブルと経路状態（US09）。
-- 経路確定で cargo に leg 列を割り当て、routing_status を ROUTED に更新する。
-- 航海参照は BC 独立性のため業務識別子 voyage_number（文字列）で保持する。
ALTER TABLE cargo
    ADD COLUMN routing_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ROUTED';

CREATE TABLE leg (
    id                       BIGSERIAL PRIMARY KEY,
    cargo_id                 BIGINT       NOT NULL REFERENCES cargo (id) ON DELETE CASCADE,
    voyage_number            VARCHAR(20)  NOT NULL,
    load_location_unlocode   VARCHAR(5)   NOT NULL,
    unload_location_unlocode VARCHAR(5)   NOT NULL,
    load_time                TIMESTAMP    NOT NULL,
    unload_time              TIMESTAMP    NOT NULL,
    seq_number               INTEGER      NOT NULL
);

CREATE INDEX idx_leg_cargo_id ON leg (cargo_id);
