-- 確定した旅程の区間（US09）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- **全行を入れ替える**（投影側の既定）。足すだけにすると、経路を設計し直した予約に
-- 古い区間が残り、旅程が二重に見える。
CREATE TABLE cargo_leg (
    booking_id VARCHAR(36) NOT NULL,
    leg_seq INTEGER NOT NULL,
    voyage_number VARCHAR(20) NOT NULL,
    load_unlocode VARCHAR(5) NOT NULL,
    unload_unlocode VARCHAR(5) NOT NULL,
    load_at TIMESTAMPTZ NOT NULL,
    unload_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (booking_id, leg_seq)
);

CREATE INDEX idx_cargo_leg_voyage ON cargo_leg (voyage_number);
