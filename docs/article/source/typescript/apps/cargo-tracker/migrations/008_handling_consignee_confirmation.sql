-- IT6: Handling Context（荷受人確認の永続化・IT5 レビュー tester M3）
-- 出典: docs/development/iteration_plan-6.md タスク 1.4、注 3。
-- CLAIM（引取）時の荷受人確認（署名または確認コード）を引き渡し証明として記録する。
-- 例外テーブル（tracking_exception_event）は目的が異なるため別マイグレーション（009）とする（注 3）。

-- Up Migration

ALTER TABLE handling_activity ADD COLUMN consignee_confirmation VARCHAR(200);
