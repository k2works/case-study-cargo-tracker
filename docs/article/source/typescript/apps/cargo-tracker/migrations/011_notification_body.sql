-- IT7: 通知本文（ADR-012）。
-- notification_record に人間可読な本文（対応報告の新到着予定日・対応方針、
-- エスカレーションの例外種別・発生地など）を保持する。実配信はこの body を素材とする。

-- Up Migration

ALTER TABLE notification_record ADD COLUMN body TEXT;
