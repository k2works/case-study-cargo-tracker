-- handling_activity（荷役作業記録・US15/US16）。SQLite 方言。
-- 引取（CLAIM）時の荷受人確認は consignee_confirmation に保持する。
CREATE TABLE handling_activity (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id             TEXT    NOT NULL,
    event_type             TEXT    NOT NULL,
    event_completion_time  TEXT    NOT NULL,
    location_unlocode      TEXT    NOT NULL,
    voyage_number          TEXT,
    consignee_confirmation TEXT,
    operator_name          TEXT,
    created_at             TEXT    NOT NULL,
    updated_at             TEXT    NOT NULL,
    version                INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_handling_activity_booking_id ON handling_activity(booking_id);
