-- IT7 US19 / US20: notification_log CHECK 制約を拡張
-- - DelayNotified (US19 遅延通知)
-- - DamageReported (US20 破損通知)
-- - LossEscalated (US20 紛失 + 管理職エスカレーション)
-- - ExceptionResponded (US19/US20 対応報告通知)
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded',
                    'DeliveryCompleted', 'ManualStatusUpdated',
                    'DelayNotified', 'DamageReported', 'LossEscalated', 'ExceptionResponded'));
