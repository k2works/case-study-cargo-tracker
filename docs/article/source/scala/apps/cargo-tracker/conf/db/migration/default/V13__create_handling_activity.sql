-- IT5 US15: 荷役作業記録（Handling Context 集約ルート）
-- data-model.md L827 準拠
CREATE TABLE handling_activity (
  id BIGSERIAL PRIMARY KEY,
  booking_id VARCHAR(20) NOT NULL,
  event_type VARCHAR(30) NOT NULL
    CHECK (event_type IN ('Receive', 'Load', 'Unload', 'Customs', 'Claim')),
  event_completion_time TIMESTAMP NOT NULL,
  location_unlocode VARCHAR(5) NOT NULL,
  voyage_number VARCHAR(20),
  operator_name VARCHAR(200),
  route_deviation BOOLEAN NOT NULL DEFAULT FALSE,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_handling_activity_booking ON handling_activity (booking_id);
CREATE INDEX idx_handling_activity_completion ON handling_activity (event_completion_time DESC);
CREATE INDEX idx_handling_activity_voyage ON handling_activity (voyage_number);
