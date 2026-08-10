-- 引取確認コードの再伝達を通知の種別に加える（US35 / IT12 持ち越し C7）。
--
-- 荷受人がコードを忘れて港に来ることは起きる。IT12 は画面に
-- 「担当営業へお問い合わせください」と案内を足したが、
-- **問い合わせを受けた営業に伝える手段が無かった**。案内した先が行き止まりである。
--
-- **再発行はしない。** 発行し直すと、元のコードを持って港に来た荷受人が弾かれる。
-- 伝えるのはいま有効なコードそのものであり、この種別が記録するのは
-- 「いつ・誰が伝えたか」である（ADR-006 により外部へは送らない）。
--
-- **他の種別と分ける。** 状態更新に混ぜると、通知履歴を見ても
-- コードを伝えたのかどうかが読めない。「伝えたつもり」の検知が目的である以上、
-- 読めなければ記録した意味が無い。
ALTER TABLE booking_notification DROP CONSTRAINT chk_notification_type;
ALTER TABLE booking_notification ADD CONSTRAINT chk_notification_type
    CHECK (notification_type IN ('ROUTE_CONFIRMED','SCHEDULE_CHANGED',
                                 'EXCEPTION_RAISED','EXCEPTION_RESOLVED','STATUS_UPDATED',
                                 'CUSTOMS_CLEARED','CLAIM_CODE_RESENT'));
