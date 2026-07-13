-- 経路通知記録テーブル（SQLite 方言・US12・data-model IT4 実装状況）
-- 確定経路を荷主に通知した記録を予約単位で保持する。
CREATE TABLE route_notification (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id            TEXT    NOT NULL,
    notified_at           TEXT    NOT NULL,
    expected_arrival_time TEXT    NOT NULL,
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL
);
CREATE INDEX ix_route_notification_booking ON route_notification (booking_id);
