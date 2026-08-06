-- IT6 US16 / US17: notification_log CHECK 制約を拡張
-- - DeliveryCompleted (US16 引取完了通知)
-- - ManualStatusUpdated (US17 貨物状態手動更新通知)
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded',
                    'DeliveryCompleted', 'ManualStatusUpdated'));
