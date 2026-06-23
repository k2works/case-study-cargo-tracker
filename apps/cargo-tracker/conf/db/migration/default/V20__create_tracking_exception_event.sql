-- IT7 US19 (遅延例外) / US20 (破損・紛失例外) 用テーブル。
-- data-model.md L1015 準拠。Tracking Context の `tracking_activity` 1 行に対して 0..N 件発生する。
CREATE TABLE tracking_exception_event (
  id BIGSERIAL PRIMARY KEY,
  tracking_id BIGINT NOT NULL REFERENCES tracking_activity (id) ON DELETE CASCADE,
  exception_type VARCHAR(50) NOT NULL
    CHECK (exception_type IN ('Delay', 'Damage', 'Lost', 'CustomsHold')),
  location_unlocode VARCHAR(5) NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  description VARCHAR(500),
  escalation_flag BOOLEAN NOT NULL DEFAULT FALSE,
  resolved_at TIMESTAMP WITH TIME ZONE,
  resolution_notes TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tracking_exception_event_tracking ON tracking_exception_event (tracking_id);
CREATE INDEX idx_tracking_exception_event_active ON tracking_exception_event (tracking_id) WHERE resolved_at IS NULL;

COMMENT ON TABLE tracking_exception_event IS '追跡例外イベント (IT7 US19 / US20)';
COMMENT ON COLUMN tracking_exception_event.escalation_flag IS 'Lost / 重大例外時の管理職エスカレーション要否';
COMMENT ON COLUMN tracking_exception_event.resolved_at IS '対応完了時刻 (NULL = 未対応)';
