-- IT7 0.12 / M6: 荷受人確認の種別を分離保持する
-- 既存の recipient_confirmation はメモ/値 (署名イメージのテキスト・確認コード等) を保持し、
-- 新設の recipient_confirmation_type で種別 (Signature / Stamp / IdCard / Code) を保持する。
ALTER TABLE handling_activity
  ADD COLUMN recipient_confirmation_type VARCHAR(20)
  CHECK (
    recipient_confirmation_type IS NULL
    OR recipient_confirmation_type IN ('Signature', 'Stamp', 'IdCard', 'Code')
  );
COMMENT ON COLUMN handling_activity.recipient_confirmation_type IS 'Claim 時の荷受人確認方法 (IT7 0.12)';
