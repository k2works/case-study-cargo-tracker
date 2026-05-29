-- IT5 3.1: CargoSnapshot ACL（bookingms の Cargo Aggregate イベントを Anti-Corruption Layer 経由で射影）。
-- handlingms は bookingms の cargo_summary を直接 JOIN しない（domain-model.md H5）ため、必要最小情報を
-- cross-service イベント（shared.* の CargoBookedEvent 等は未だ存在しないため、IT5 では bookingms の
-- ローカル CargoBookedEvent / CargoRoutedEvent を Kafka 経由で購読する。なお shared 化は将来検討）。

CREATE TABLE IF NOT EXISTS cargo_snapshot (
    booking_id           VARCHAR(36)  NOT NULL,
    tracking_number      VARCHAR(25),                          -- CargoTrackedEvent 受信時に確定
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    cargo_type           VARCHAR(16)  NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (booking_id)
);

CREATE INDEX IF NOT EXISTS idx_cargo_snapshot_tracking_number ON cargo_snapshot (tracking_number);
