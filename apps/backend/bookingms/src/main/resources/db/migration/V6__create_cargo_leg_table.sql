-- 確定旅程（Cargo Itinerary）の輸送区間 Read Model（US11 / data-model.md cargo_leg）
-- 経路確定（CargoRoutedEvent）で booking_id 単位に確定する。leg_seq は 1 始まりの順序。
CREATE TABLE IF NOT EXISTS cargo_leg (
    booking_id      VARCHAR(36) NOT NULL,
    leg_seq         INTEGER     NOT NULL,
    voyage_number   VARCHAR(20) NOT NULL,
    load_unlocode   VARCHAR(5)  NOT NULL,
    unload_unlocode VARCHAR(5)  NOT NULL,
    load_at         TIMESTAMP   NOT NULL,
    unload_at       TIMESTAMP   NOT NULL,
    PRIMARY KEY (booking_id, leg_seq)
);

CREATE INDEX IF NOT EXISTS idx_cargo_leg_voyage_number
    ON cargo_leg (voyage_number);
