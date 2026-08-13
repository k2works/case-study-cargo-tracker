-- 精算書の発行を知らせる通知種別（US23）。
--
-- US23 は「精算書が荷主にメール通知される」と述べている。外部へは送らず
-- （ADR-006）、伝えた事実を記録する。
--
-- **状態更新の通知に混ぜない。** 混ぜると「請求書を送ったか」を通知履歴から
-- 読めない。荷主から「請求書が届いていない」と言われたときに答えるための記録である。
ALTER TABLE booking_notification DROP CONSTRAINT chk_notification_type;
ALTER TABLE booking_notification ADD CONSTRAINT chk_notification_type
    CHECK (notification_type IN ('ROUTE_CONFIRMED','SCHEDULE_CHANGED',
                                 'EXCEPTION_RAISED','EXCEPTION_RESOLVED','STATUS_UPDATED',
                                 'CUSTOMS_CLEARED','CLAIM_CODE_RESENT','INVOICE_ISSUED'));
