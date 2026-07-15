-- notification_log（荷主通知の記録・US12）。SQLite 方言。
-- 経路確定などの通知イベントを記録する最小実装。実送信（メール等）は後続 IT で差し替える。
CREATE TABLE notification_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id  TEXT    NOT NULL,
    recipient   TEXT    NOT NULL,
    message     TEXT    NOT NULL,
    notified_at TEXT    NOT NULL,
    created_at  TEXT    NOT NULL
);

CREATE INDEX idx_notification_log_booking_id ON notification_log(booking_id);
