-- 予約の最終更新（US32）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 「修正の履歴が残る」（US32 §受入基準 4）は Event Store が持つ。投影には最終更新の
-- 2 列だけを置き、変更内容の履歴テーブルは作らない。同じ事実を 2 か所で持つとずれる。
ALTER TABLE cargo_summary ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE cargo_summary ADD COLUMN updated_by VARCHAR(50);
