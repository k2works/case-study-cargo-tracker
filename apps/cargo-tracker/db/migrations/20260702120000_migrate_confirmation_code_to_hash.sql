-- 確認コードの平文列 `code` を bcrypt ハッシュ列 `code_hash` に置き換える
-- (T5-02 Phase 3b, SEC-04, IT6)
--
-- data-model.md §confirmation_code の設計 (code_hash VARCHAR(72) bcrypt cost=10)
-- に完全準拠させる。IT5 段階では実装簡略化のため平文カラム code を使用していたが、
-- T5-02 Phase 3 で bcrypt 化を完了する。
--
-- 移行時点で既存 confirmation_code レコードは開発環境のみに存在するため、
-- データ移行は行わず単純に列を置換する。fresh な発行から Application 層で
-- hashSecret 経由の bcrypt ハッシュのみが保存される。

-- migrate:up
ALTER TABLE confirmation_code DROP COLUMN code;
ALTER TABLE confirmation_code ADD COLUMN code_hash VARCHAR(72) NOT NULL;

-- migrate:down
ALTER TABLE confirmation_code DROP COLUMN code_hash;
ALTER TABLE confirmation_code ADD COLUMN code VARCHAR(6) NOT NULL;
