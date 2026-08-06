-- 楽観ロック準備（IT1 レビュー H5 / IT2 タスク 0.11）
-- 更新系操作を持つ集約ルートテーブルに version カラムを付与。
-- data-model.md 1175「楽観ロック規約: SET version = version + 1 ... WHERE id = ? AND version = ?」
-- リポジトリの UPDATE は version をインクリメントする。IT2 時点では更新元が UI に存在しないため、
-- 競合検出（更新行数 0 の DomainError.ConcurrentModification 返却）の完全な活性化は IT3 で実施する。

ALTER TABLE shipper  ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE estimate ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cargo    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
