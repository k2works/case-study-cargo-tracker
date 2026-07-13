-- 経路通知記録テーブル（PostgreSQL 方言・US12・data-model IT4 実装状況）
-- 確定経路を荷主に通知した記録を予約単位で保持する。
CREATE TABLE route_notification (
    id                    BIGSERIAL   PRIMARY KEY,
    booking_id            VARCHAR(20) NOT NULL,
    notified_at           TIMESTAMP   NOT NULL,
    expected_arrival_time TIMESTAMP   NOT NULL,
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_route_notification_booking ON route_notification (booking_id);
