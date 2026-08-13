-- 予約キャンセルの承認・却下を知らせる通知種別（US30）。
--
-- US30 は「承認されると荷主に通知される」「却下理由が申請者と荷主に通知される」と
-- 述べている。外部へは送らず（ADR-006）、伝えた事実を記録する。
--
-- **申請の時点では足さない。** 荷主にはまだ何も確定しておらず、
-- 「キャンセルを申請しました」を通知として残すと、
-- **承認されたのかどうかが履歴から読めなくなる**。
--
-- **社内の承認依頼は通知にしない**（US36 の先例）。booking_notification は
-- **荷主向け**の記録であり、社内の作業依頼を混ぜると
-- INVOICE_ISSUED 等の意味（荷主に伝えた事実）が壊れる。
-- 追跡管理者への依頼は承認待ち一覧とダッシュボードのカードで届ける。
ALTER TABLE booking_notification DROP CONSTRAINT chk_notification_type;
ALTER TABLE booking_notification ADD CONSTRAINT chk_notification_type
    CHECK (notification_type IN ('ROUTE_CONFIRMED','SCHEDULE_CHANGED',
                                 'EXCEPTION_RAISED','EXCEPTION_RESOLVED','STATUS_UPDATED',
                                 'CUSTOMS_CLEARED','CLAIM_CODE_RESENT','INVOICE_ISSUED',
                                 'CANCELLATION_APPROVED','CANCELLATION_REJECTED'));
