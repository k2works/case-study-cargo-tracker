-- carrier_movement テーブルに基本運賃列を追加
ALTER TABLE carrier_movement ADD COLUMN base_fare_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE carrier_movement ADD COLUMN base_fare_currency VARCHAR(3) NOT NULL DEFAULT 'JPY';

-- テストデータ: V001（東京 → ニューヨーク直行便）
UPDATE carrier_movement SET base_fare_amount = 500000
WHERE voyage_id = (SELECT id FROM voyage WHERE voyage_number = 'V001');

-- テストデータ: V002（東京 → 上海 → ニューヨーク）
UPDATE carrier_movement SET base_fare_amount = 150000
WHERE voyage_id = (SELECT id FROM voyage WHERE voyage_number = 'V002')
  AND departure_location_unlocode = 'JPTYO';
UPDATE carrier_movement SET base_fare_amount = 300000
WHERE voyage_id = (SELECT id FROM voyage WHERE voyage_number = 'V002')
  AND departure_location_unlocode = 'CNSHA';

-- テストデータ: V003（東京 → ロサンゼルス直行便）
UPDATE carrier_movement SET base_fare_amount = 420000
WHERE voyage_id = (SELECT id FROM voyage WHERE voyage_number = 'V003');
