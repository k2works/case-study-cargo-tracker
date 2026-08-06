-- 例外通知記録テーブル（PostgreSQL 方言・US19/US20）
-- 例外検知（TrackingExceptionDetectedEvent）に応じて荷主・管理職への通知を append-only で記録する。
-- メール実送信基盤は後続 IT。本 IT では通知記録で代替する（IT4/IT5 の通知記録方針を踏襲）。
CREATE TABLE exception_notification (
    id                  BIGSERIAL   PRIMARY KEY,
    tracking_number     VARCHAR(20) NOT NULL,
    booking_id          VARCHAR(20) NOT NULL,
    recipient_type      VARCHAR(20) NOT NULL,   -- SHIPPER / MANAGEMENT
    exception_type      VARCHAR(50) NOT NULL,
    message             TEXT        NOT NULL,
    notified_at         TIMESTAMP   NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL
);
CREATE INDEX ix_exception_notification_tracking ON exception_notification (tracking_number);
