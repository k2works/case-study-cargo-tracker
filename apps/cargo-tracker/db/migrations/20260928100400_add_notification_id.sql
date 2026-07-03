-- migrate:up

-- IT7 ADR-0013 Phase 1: Notification 主キー設計
--
-- notification テーブルに notification_id (UUID) カラムを追加。
-- 業務キー変更 (subject 修正 / 再送信) に対して耐性のあるサロゲート識別子を
-- 提供する。既存レコードには gen_random_uuid() で自動採番される。
--
-- Phase 2 で SendClaimNotificationCommand が UUID v4 を Application 層で生成し、
-- Phase 3 で updateNotification の WHERE 節が notification_id ベースに移行する
-- 予定。本 migration では Phase 1 (スキーマ拡張のみ) を実施する。
--
-- 参照: docs/adr/0013-notification-primary-key-design.md
--       docs/design/data-model.md §notification (Phase 1 で反映予定)

ALTER TABLE notification
  ADD COLUMN notification_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_notification_notification_id
  ON notification (notification_id);

-- pgcrypto が有効でない環境向けフォールバック:
-- CREATE EXTENSION IF NOT EXISTS pgcrypto; を運用側で事前に実行する。

-- migrate:down

DROP INDEX IF EXISTS idx_notification_notification_id;
ALTER TABLE notification DROP COLUMN IF EXISTS notification_id;
