-- 例外通知記録テーブル（SQLite 方言・US19/US20）
-- 例外検知（TrackingExceptionDetectedEvent）に応じて荷主・管理職への通知を append-only で記録する。
-- メール実送信基盤は後続 IT。本 IT では通知記録で代替する（IT4/IT5 の通知記録方針を踏襲）。
CREATE TABLE exception_notification (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_number     TEXT    NOT NULL,
    booking_id          TEXT    NOT NULL,
    recipient_type      TEXT    NOT NULL,   -- SHIPPER / MANAGEMENT
    exception_type      TEXT    NOT NULL,
    message             TEXT    NOT NULL,
    notified_at         TEXT    NOT NULL,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL
);
CREATE INDEX ix_exception_notification_tracking ON exception_notification (tracking_number);
