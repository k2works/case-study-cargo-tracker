-- 部分入金対応カラム追加（IT9 A1.5 / ADR-0020 / US26）
-- BalanceTracker（domain）の累積入金額を Read Model にも反映する。
-- invoice.paid_so_far: 累積入金額（部分入金時は < total_amount、完全入金時は = total_amount）
-- payment.is_partial:  各入金の部分入金フラグ（Stripe webhook 経由の部分入金は TRUE）

ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS paid_so_far NUMERIC(14,2) NOT NULL DEFAULT 0;

ALTER TABLE invoice
    ADD CONSTRAINT chk_invoice_paid_so_far CHECK (paid_so_far >= 0 AND paid_so_far <= total_amount);

ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS is_partial BOOLEAN NOT NULL DEFAULT FALSE;
