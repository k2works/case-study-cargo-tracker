-- 貨物状態の更新を通知の種別に加える（US17 / IT8）。
--
-- US17 の受入基準「状態変更の種類に応じて荷主への通知が送信される」を、
-- **US12 で作った通知記録の仕組みに載せて**満たす（ADR-006 により外部へは送らない）。
ALTER TABLE booking_notification
    DROP CONSTRAINT chk_notification_type;

ALTER TABLE booking_notification
    ADD CONSTRAINT chk_notification_type
    CHECK (notification_type IN ('ROUTE_CONFIRMED', 'SCHEDULE_CHANGED',
                                 'EXCEPTION_RAISED', 'STATUS_UPDATED'));
