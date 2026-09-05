-- 航海の最終更新（US25）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 「修正の履歴が残る」は Event Store が持つ。投影には最終更新の 2 列だけを置き、
-- 変更内容の履歴テーブルは作らない（同じ事実を 2 か所で持つと必ずずれる）。
ALTER TABLE voyage ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE voyage ADD COLUMN updated_by VARCHAR(50);
