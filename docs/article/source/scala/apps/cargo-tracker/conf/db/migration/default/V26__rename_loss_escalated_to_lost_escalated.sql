-- IT8 0.2 (H2 解消): NotificationType.LossEscalated → LostEscalated にリネーム
-- ExceptionType.Lost (状態) と命名統一する（ユビキタス言語）
-- 既存データを LostEscalated に更新し、CHECK 制約も差し替える
UPDATE notification_log
   SET type = 'LostEscalated'
 WHERE type = 'LossEscalated';

ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded',
                    'DeliveryCompleted', 'ManualStatusUpdated',
                    'DelayNotified', 'DamageReported', 'LostEscalated', 'ExceptionResponded'));
