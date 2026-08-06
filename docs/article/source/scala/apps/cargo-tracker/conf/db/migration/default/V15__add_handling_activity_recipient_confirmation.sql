-- IT6 US16 タスク 1.3: 引取作業 (Claim) 時の荷受人確認情報を保持する
-- ドメイン: HandlingActivity.recipientConfirmation (Option[String])
-- 不変条件: HandlingType = 'Claim' の場合は NOT NULL (アプリケーション層で検証)
ALTER TABLE handling_activity
  ADD COLUMN recipient_confirmation VARCHAR(120);
COMMENT ON COLUMN handling_activity.recipient_confirmation IS 'Claim 時の荷受人署名 or 確認コード (US16)';
