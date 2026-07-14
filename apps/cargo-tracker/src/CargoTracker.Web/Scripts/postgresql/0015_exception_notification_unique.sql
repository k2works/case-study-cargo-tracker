-- 例外通知の冪等キー（PostgreSQL 方言・IT6 レビュー M1 / ADR-0009 コンプライアンス）
-- 同一イベントの再配信（at-least-once）で通知が二重記録されないよう、自然キーに一意制約を課す。
-- notified_at は検知＝OccurredAt / 解決＝ResolvedAt でイベントごとに決定的。
CREATE UNIQUE INDEX uk_exception_notification_natural
    ON exception_notification (tracking_number, recipient_type, exception_type, notified_at);
