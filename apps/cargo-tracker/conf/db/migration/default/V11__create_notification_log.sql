-- IT4 タスク 3.2 / US12 + US13: 通知ログテーブル。
-- 1 予約に対し複数通知を時系列で蓄積する（PK は自動採番）。
-- IT4 はメール送信を行わず DB ログのみ。IT5+ で MailHog 連携を追加。
-- 計画書上は V10 だったが、V10 は cargo.itinerary_voyages 追加に充てたため V11 に繰り下げ。

CREATE TABLE notification_log (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  VARCHAR(20) NOT NULL,
    type        VARCHAR(30) NOT NULL,
    sent_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    payload     TEXT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notification_log_type
        CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled'))
);

CREATE INDEX idx_notification_log_booking_sent ON notification_log (booking_id, sent_at DESC);
