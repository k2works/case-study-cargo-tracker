-- IT5 レビュー H3 対応：CargoDeliveredEvent の cross-service 発行を冪等化する。
-- delivered_published_at は CargoDeliveredEventPublisher が tracking_summary を UPDATE して
-- 「未発行のみ」発行する条件カラム。NULL のときのみ発行 → UPDATE で NOT NULL 化することで
-- event store リプレイ時の二度発行を防ぐ。

ALTER TABLE tracking_summary
    ADD COLUMN IF NOT EXISTS delivered_published_at TIMESTAMP;
