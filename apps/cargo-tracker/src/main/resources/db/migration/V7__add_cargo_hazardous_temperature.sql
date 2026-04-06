-- 危険物貨物フィールド
ALTER TABLE cargo ADD COLUMN hazardous_class VARCHAR(10);
ALTER TABLE cargo ADD COLUMN un_number VARCHAR(10);
ALTER TABLE cargo ADD COLUMN proper_shipping_name VARCHAR(200);

-- 冷凍・冷蔵貨物フィールド
ALTER TABLE cargo ADD COLUMN min_temperature NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN max_temperature NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN temperature_unit VARCHAR(20);
