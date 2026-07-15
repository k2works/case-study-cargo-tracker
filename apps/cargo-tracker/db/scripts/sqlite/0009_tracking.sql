-- tracking_activity / tracking_handling_event（貨物追跡・US14/US15/US17/US18）。SQLite 方言。
-- 状態はイベント履歴からの導出値だが、クエリ用に transport_status を非正規化保持する。
-- access_token は公開追跡ページ（US18・未認証）用の推測困難トークン。
CREATE TABLE tracking_activity (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_number  TEXT    NOT NULL UNIQUE,
    booking_id       TEXT    NOT NULL,
    transport_status TEXT    NOT NULL DEFAULT 'NOT_RECEIVED',
    access_token     TEXT    NOT NULL UNIQUE,
    created_at       TEXT    NOT NULL,
    updated_at       TEXT    NOT NULL,
    version          INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE tracking_handling_event (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_id       INTEGER NOT NULL REFERENCES tracking_activity(id),
    event_type        TEXT    NOT NULL,
    event_time        TEXT    NOT NULL,
    location_unlocode TEXT,
    voyage_number     TEXT,
    seq_number        INTEGER NOT NULL,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL
);

CREATE INDEX idx_tracking_handling_event_tracking_id ON tracking_handling_event(tracking_id);
