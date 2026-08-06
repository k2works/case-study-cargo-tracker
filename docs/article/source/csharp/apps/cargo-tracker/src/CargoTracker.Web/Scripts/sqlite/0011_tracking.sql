-- 追跡テーブル（SQLite 方言・US14/US15/US17・data-model IT5 実装状況）
-- 追跡番号発行（US14）で tracking_activity を作成し、荷役・手動更新で tracking_handling_event を追記する。
CREATE TABLE tracking_activity (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_number     TEXT    NOT NULL,
    booking_id          TEXT    NOT NULL,
    transport_status    TEXT    NOT NULL DEFAULT 'NOT_RECEIVED',
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_tracking_activity_number UNIQUE (tracking_number),
    CONSTRAINT uk_tracking_activity_booking UNIQUE (booking_id)
);

CREATE TABLE tracking_handling_event (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_id         INTEGER NOT NULL REFERENCES tracking_activity(id),
    seq_number          INTEGER NOT NULL,
    event_type          TEXT    NOT NULL,
    event_time          TEXT    NOT NULL,
    location_unlocode   TEXT    NOT NULL,
    voyage_number       TEXT,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    CONSTRAINT uk_tracking_event_seq UNIQUE (tracking_id, seq_number),
    CONSTRAINT ck_tracking_event_seq_positive CHECK (seq_number >= 1)
);
