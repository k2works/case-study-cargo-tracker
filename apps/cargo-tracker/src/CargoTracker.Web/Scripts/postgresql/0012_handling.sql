-- 荷役作業記録テーブル（PostgreSQL 方言・US15/US16・data-model IT5 実装状況）
-- 荷役作業員が受領・積込・荷降し・引取を記録する。追跡イベントと状態同期は ACL で連携する。
CREATE TABLE handling_activity (
    id                      BIGSERIAL   PRIMARY KEY,
    booking_id              VARCHAR(20) NOT NULL,
    event_type              VARCHAR(30) NOT NULL,
    event_completion_time   TIMESTAMP   NOT NULL,
    location_unlocode       VARCHAR(5)  NOT NULL,
    voyage_number           VARCHAR(20),
    created_at              TIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP   NOT NULL
);
CREATE INDEX ix_handling_activity_booking ON handling_activity (booking_id);
