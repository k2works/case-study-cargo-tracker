-- IT9 0.8 (R4 解消): Invoice.refund 実装のため refunded_at / refund_reason 列追加
-- ADR 0019 案 B の PaymentStatus 完成版 (Refunded 含む) のサポート
-- 計画上は V30 だったが V28 までしか存在しないため CLAUDE.md Flyway 採番ルール (max + 1) に従い V29 を採番
ALTER TABLE invoice
    ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN refund_reason VARCHAR(500) NULL;

COMMENT ON COLUMN invoice.refunded_at IS '返金時刻 (IT9 R4 / Confirmed → Refunded 遷移時)';
COMMENT ON COLUMN invoice.refund_reason IS '返金理由 (任意、最大 500 文字)';
