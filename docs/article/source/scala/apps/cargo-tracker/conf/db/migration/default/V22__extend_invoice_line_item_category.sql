-- IT7 0.9 / H6: 既存 invoice_line_item (V17 で作成、line_no / description / amount) に
-- 料金内訳種別 category を追加し、ドメインの LineItemCategory enum (Distance / Weight / CargoType / Discount / Other)
-- に対応させる。既存行 (現状 0 件想定) は 'Other' をデフォルトとして埋める。
ALTER TABLE invoice_line_item
  ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'Other'
  CHECK (category IN ('Distance', 'Weight', 'CargoType', 'Discount', 'Other'));

COMMENT ON COLUMN invoice_line_item.category IS '料金内訳種別 (IT7 0.9)';
