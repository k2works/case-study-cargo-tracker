-- 有効な請求書は予約ごとに 1 通（[ADR-028] 決定 3）。
-- 取り消した請求書は void_marker に請求番号が入るため、同じ予約で共存できる。
ALTER TABLE invoice ADD CONSTRAINT uk_invoice_booking_active UNIQUE (booking_id, void_marker);
