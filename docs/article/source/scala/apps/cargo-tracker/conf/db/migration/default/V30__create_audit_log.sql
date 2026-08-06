-- IT9 US30: システム監査ログ (ISO 27001 監査対応)
-- 計画上は V29 だったが IT9 0.8 (invoice refund 列追加) で V29 を消費したため V30 として実装
CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY,
  operator VARCHAR(50) NOT NULL,
  action VARCHAR(50) NOT NULL
    CHECK (action IN ('CancelExceptionResolution', 'AppendResolutionComment',
                      'IssuePayment', 'ConfirmPayment', 'Refund', 'ImportPaymentsBatch')),
  target_type VARCHAR(50) NOT NULL,
  target_id VARCHAR(50) NOT NULL,
  before TEXT,
  after TEXT,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_operator ON audit_log (operator);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_action_occurred ON audit_log (action, occurred_at DESC);

COMMENT ON TABLE audit_log IS 'システム監査ログ (IT9 US30 / ISO 27001 対応)';
COMMENT ON COLUMN audit_log.before IS '変更前状態 (JSON、NULL 可)';
COMMENT ON COLUMN audit_log.after IS '変更後状態 (JSON、insert 後 UPDATE 禁止)';
