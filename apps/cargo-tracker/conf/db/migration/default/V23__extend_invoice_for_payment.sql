-- IT8 US23 ADR 0019 案 B: Payment は Invoice 集約内のステータスとして表現する
-- invoice テーブルに支払期日と入金参照コードを追加し、PaymentStatus enum に NotIssued を追加
ALTER TABLE invoice
    ADD COLUMN due_date DATE NULL,
    ADD COLUMN payment_reference VARCHAR(64) NULL;

-- payment_status CHECK 制約を NotIssued 含む形に差し替え
-- 既存 Pending を NotIssued に置き換えはしない（既存データは US21 で Pending として正しく入金待ち状態）
ALTER TABLE invoice DROP CONSTRAINT IF EXISTS ck_invoice_payment_status;
ALTER TABLE invoice ADD CONSTRAINT ck_invoice_payment_status
    CHECK (payment_status IN ('NotIssued', 'Pending', 'Overdue', 'Confirmed', 'Refunded'));

COMMENT ON COLUMN invoice.due_date IS '支払期日 (IT8 ADR 0019 案 B / US23)';
COMMENT ON COLUMN invoice.payment_reference IS '入金参照コード（手動入力、外部決済 ID は IT9 で連携予定）';

-- corporate_discount_policy テーブルは US22 では未使用 (Shipper.discountRate を直接参照) のため IT9 申し送り
