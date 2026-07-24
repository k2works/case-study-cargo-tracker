-- デモ用シードデータ（荷主・貨物予約）
-- 開発・デモの初期表示用。荷主参照は業務識別子 shipper_code で行う。

INSERT INTO shipper (shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate) VALUES
    ('SHP-DEMO0001', 'INDIVIDUAL', '山田太郎', 'taro@example.com', '03-1234-5678', '東京都千代田区丸の内1-1-1', NULL, NULL),
    ('SHP-DEMO0002', 'CORPORATE', '株式会社サンプル物流', 'corp@example.com', '06-1111-2222', '大阪府大阪市北区梅田2-2-2', 'CN-2026-001', 0.1000)
ON CONFLICT (shipper_code) DO NOTHING;

INSERT INTO cargo (
    booking_id, shipper_code, booking_status, cargo_type, weight_kg,
    spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
    booking_amount_value, booking_amount_currency
) VALUES
    ('BKG-DEMO0001', 'SHP-DEMO0001', 'PRELIMINARY', 'GENERAL', 1200.500, 'JPTYO', 'DEHAM', '2026-09-01', 0, 'JPY'),
    ('BKG-DEMO0002', 'SHP-DEMO0002', 'PRELIMINARY', 'REFRIGERATED', 800.000, 'JPOSA', 'USLAX', '2026-10-15', 0, 'JPY')
ON CONFLICT (booking_id) DO NOTHING;
