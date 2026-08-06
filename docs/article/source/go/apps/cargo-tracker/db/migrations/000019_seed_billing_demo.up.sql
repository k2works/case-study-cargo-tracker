-- 精算フロー（US21/US22/US23）のデモ・E2E 用シードデータ。
-- 引取済み（CLAIMED）の法人荷主予約を用意し、料金算出→法人割引→精算書発行→
-- 入金確認→SETTLED のフルフローを画面から通せるようにする。
INSERT INTO cargo (
    booking_id, shipper_code, booking_status, cargo_type, weight_kg,
    spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
    booking_amount_value, booking_amount_currency, transport_status, tracking_number
) VALUES
    ('BKG-BILL0001', 'SHP-DEMO0002', 'ASSIGNED', 'GENERAL', 1000.000, 'JPTYO', 'USLAX', '2026-09-30',
     0, 'JPY', 'CLAIMED', 'TRK-20260101-0001')
ON CONFLICT (booking_id) DO NOTHING;
