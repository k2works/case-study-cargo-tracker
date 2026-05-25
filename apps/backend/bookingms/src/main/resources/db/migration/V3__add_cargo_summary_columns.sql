-- US04 貨物予約登録のため cargo_summary に貨物詳細カラムを追加。
-- US05 で hazard_* / temperature_* も同時に作成しておく（実際の利用は US05）。
ALTER TABLE cargo_summary ADD COLUMN weight_kg NUMERIC(12, 2);
ALTER TABLE cargo_summary ADD COLUMN length_cm INTEGER;
ALTER TABLE cargo_summary ADD COLUMN width_cm INTEGER;
ALTER TABLE cargo_summary ADD COLUMN height_cm INTEGER;
ALTER TABLE cargo_summary ADD COLUMN quantity INTEGER;
ALTER TABLE cargo_summary ADD COLUMN product_name VARCHAR(200);
ALTER TABLE cargo_summary ADD COLUMN estimated_amount NUMERIC(14, 2);
ALTER TABLE cargo_summary ADD COLUMN estimated_currency VARCHAR(3);
ALTER TABLE cargo_summary ADD COLUMN hazard_imo_class VARCHAR(20);
ALTER TABLE cargo_summary ADD COLUMN hazard_un_number VARCHAR(20);
ALTER TABLE cargo_summary ADD COLUMN hazard_declaration TEXT;
ALTER TABLE cargo_summary ADD COLUMN temperature_min_c NUMERIC(5, 2);
ALTER TABLE cargo_summary ADD COLUMN temperature_max_c NUMERIC(5, 2);
