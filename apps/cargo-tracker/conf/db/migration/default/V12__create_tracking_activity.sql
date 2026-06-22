-- IT5 US14: Tracking Context 集約ルート（追跡レコード）
-- data-model.md L782 準拠 / ADR 0010
CREATE TABLE tracking_activity (
  id BIGSERIAL PRIMARY KEY,
  tracking_number VARCHAR(20) NOT NULL,
  booking_id VARCHAR(20) NOT NULL,
  transport_status VARCHAR(30) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_tracking_activity_tracking_number UNIQUE (tracking_number),
  CONSTRAINT uk_tracking_activity_booking UNIQUE (booking_id)
);
CREATE INDEX idx_tracking_activity_booking ON tracking_activity (booking_id);
CREATE INDEX idx_tracking_activity_transport_status ON tracking_activity (transport_status);

-- cargo に tracking_number カラムを追加（data-model.md L732）
ALTER TABLE cargo ADD COLUMN tracking_number VARCHAR(20);
CREATE INDEX idx_cargo_tracking_number ON cargo (tracking_number);

-- 注: notification_log の CHECK 制約への TrackingIssued / HandlingRecorded 追加は V14 にまとめて実施
