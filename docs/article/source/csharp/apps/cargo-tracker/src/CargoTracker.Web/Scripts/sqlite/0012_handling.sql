-- 荷役作業記録テーブル（SQLite 方言・US15/US16・data-model IT5 実装状況）
-- 荷役作業員が受領・積込・荷降し・引取を記録する。追跡イベントと状態同期は ACL で連携する。
CREATE TABLE handling_activity (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id              TEXT    NOT NULL,
    event_type              TEXT    NOT NULL,
    event_completion_time   TEXT    NOT NULL,
    location_unlocode       TEXT    NOT NULL,
    voyage_number           TEXT,
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL
);
CREATE INDEX ix_handling_activity_booking ON handling_activity (booking_id);
