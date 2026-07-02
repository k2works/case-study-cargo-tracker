-- Notification BC の通知レコード (US26 Phase 7, IT6)
-- data-model.md §notification (Cross-cutting) に対応。
--
-- 1 予約に対して複数通知が発行され得るため、booking_id にはインデックスのみ。
-- 状態 (status) は Pending / Sent / Failed の 3 値。
-- 配信手段 (channel) は Log / EmailMock / PrintableHtml。

-- migrate:up
CREATE TABLE notification (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        VARCHAR(20) NOT NULL,
    channel           VARCHAR(20) NOT NULL
                      CHECK (channel IN ('Log', 'EmailMock', 'PrintableHtml')),
    subject           VARCHAR(200) NOT NULL,
    body              TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'Pending'
                      CHECK (status IN ('Pending', 'Sent', 'Failed')),
    created_at        TIMESTAMPTZ NOT NULL,
    sent_at           TIMESTAMPTZ,
    failure_reason    TEXT,
    version           INTEGER NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_booking
  ON notification (booking_id, created_at DESC);

-- migrate:down
DROP TABLE notification;
