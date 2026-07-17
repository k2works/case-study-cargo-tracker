-- invoice に消費税（tax_rate / tax_amount）を追加（US22 消費税・付加料金・IT8）。SQLite 方言。
-- 消費税 = 割引後小計（final_amount）× tax_rate、請求総額 = final_amount + tax_amount。
-- 既存行との互換のため nullable で追加する（未計上は NULL = 税額 0 扱い）。
ALTER TABLE invoice ADD COLUMN tax_rate NUMERIC;
ALTER TABLE invoice ADD COLUMN tax_amount INTEGER;
