-- IT8 US23 (ADR 0019 案 B): notification_log CHECK 制約に支払関連 3 種を追加
-- - PaymentRequested (US23 入金発行通知)
-- - PaymentConfirmed (US23 入金確認通知)
-- - OverdueAlerted (US23 期限超過通知)
--
-- 計画上は V24 だったが、IT8 0.x で V26 (LossEscalated rename) を先行作成済のため、
-- Flyway の Out-Of-Order を避けるため V27 として実装。
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded',
                    'DeliveryCompleted', 'ManualStatusUpdated',
                    'DelayNotified', 'DamageReported', 'LostEscalated', 'ExceptionResponded',
                    'PaymentRequested', 'PaymentConfirmed', 'OverdueAlerted'));
