-- IT5 US15: Tracking Context 集約内エンティティ（追跡イベント）
-- data-model.md L795 準拠
CREATE TABLE tracking_handling_event (
  id BIGSERIAL PRIMARY KEY,
  tracking_id BIGINT NOT NULL REFERENCES tracking_activity (id) ON DELETE CASCADE,
  event_type VARCHAR(30) NOT NULL
    CHECK (event_type IN ('Receive', 'Load', 'Unload', 'Customs', 'Claim')),
  event_time TIMESTAMP NOT NULL,
  location_unlocode VARCHAR(5) NOT NULL,
  voyage_number VARCHAR(20),
  route_deviation BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tracking_handling_event_tracking ON tracking_handling_event (tracking_id, event_time);

-- notification_log CHECK 制約に HandlingRecorded を追加（US15）
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded'));
