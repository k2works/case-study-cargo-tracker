-- tracking_exception_event（追跡例外イベント・US19 遅延 / US20 破損・紛失）。SQLite 方言。
-- ExceptionResolution DU（Unresolved of escalated / Resolved of resolvedAt）を
-- escalation_flag（INTEGER 0/1）＋ resolved_at（NULL 可）の 2 カラムへ写像する。
-- 読み出し時、resolved_at が NULL なら Unresolved escalation_flag、非 NULL なら Resolved resolved_at に復元する。
CREATE TABLE tracking_exception_event (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_id      INTEGER NOT NULL REFERENCES tracking_activity(id),
    exception_type   TEXT    NOT NULL,
    location_unlocode TEXT,
    occurred_at      TEXT    NOT NULL,
    escalation_flag  INTEGER NOT NULL DEFAULT 0,
    description      TEXT,
    resolved_at      TEXT,
    resolution_notes TEXT,
    seq_number       INTEGER NOT NULL,
    created_at       TEXT    NOT NULL,
    updated_at       TEXT    NOT NULL
);

CREATE INDEX idx_tracking_exception_event_tracking_id ON tracking_exception_event(tracking_id);
