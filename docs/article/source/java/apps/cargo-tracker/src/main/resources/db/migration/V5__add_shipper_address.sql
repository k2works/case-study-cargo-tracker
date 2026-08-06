-- shipper テーブルに住所カラムを追加
ALTER TABLE shipper ADD COLUMN address VARCHAR(500);

-- discount_rate の上限を 0.30 に変更
ALTER TABLE shipper DROP CONSTRAINT chk_discount_rate;
ALTER TABLE shipper ADD CONSTRAINT chk_discount_rate CHECK (
    discount_rate IS NULL OR (discount_rate >= 0 AND discount_rate <= 0.3000)
);
