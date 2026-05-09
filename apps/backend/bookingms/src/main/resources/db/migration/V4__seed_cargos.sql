-- Demo seed data for bookings and routing assignments
-- Covers various booking statuses for demo operations

-- PRELIMINARY: 仮予約（新規予約・経路未割当）
INSERT INTO cargo (booking_id, shipper_id, booking_status, transport_status, routing_status,
                   cargo_type, weight_kg,
                   spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
                   booking_amount_value, booking_amount_currency)
VALUES
    ('DEMO0000001A', 1, 'PRELIMINARY', 'NOT_RECEIVED', 'NOT_ROUTED',
     'GENERAL', 500.000, 'JPTYO', 'CNSHA', '2030-03-31',
     150000, 'JPY'),
    ('DEMO0000002B', 1, 'PRELIMINARY', 'NOT_RECEIVED', 'NOT_ROUTED',
     'REFRIGERATED', 200.000, 'JPTYO', 'SGSIN', '2030-04-15',
     280000, 'JPY'),
    ('DEMO0000003C', 2, 'PRELIMINARY', 'NOT_RECEIVED', 'NOT_ROUTED',
     'GENERAL', 1200.000, 'CNSHA', 'NLRTM', '2030-05-01',
     520000, 'JPY');

-- ROUTE_PROPOSED: 経路提案済み（経路設計完了・荷主確認待ち）
INSERT INTO cargo (booking_id, shipper_id, booking_status, transport_status, routing_status,
                   cargo_type, weight_kg,
                   spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
                   booking_amount_value, booking_amount_currency)
VALUES
    ('DEMO0000004D', 1, 'ROUTE_PROPOSED', 'NOT_RECEIVED', 'ROUTED',
     'GENERAL', 800.000, 'JPTYO', 'CNSHA', '2030-03-15',
     240000, 'JPY'),
    ('DEMO0000005E', 2, 'ROUTE_PROPOSED', 'NOT_RECEIVED', 'ROUTED',
     'HAZARDOUS', 300.000, 'CNSHA', 'USLAX', '2030-04-30',
     390000, 'JPY');

-- CONFIRMED: 確定済み（経路確定・輸送スケジュール割当待ち）
INSERT INTO cargo (booking_id, shipper_id, booking_status, transport_status, routing_status,
                   cargo_type, weight_kg,
                   spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
                   booking_amount_value, booking_amount_currency)
VALUES
    ('DEMO0000006F', 1, 'CONFIRMED', 'NOT_RECEIVED', 'ROUTED',
     'GENERAL', 600.000, 'JPTYO', 'CNSHA', '2030-02-28',
     180000, 'JPY'),
    ('DEMO0000007G', 2, 'CONFIRMED', 'NOT_RECEIVED', 'ROUTED',
     'GENERAL', 450.000, 'JPTYO', 'KRPUS', '2030-03-10',
     130000, 'JPY'),
    ('DEMO0000008H', 1, 'CONFIRMED', 'NOT_RECEIVED', 'ROUTED',
     'REFRIGERATED', 250.000, 'CNSHA', 'SGSIN', '2030-03-20',
     200000, 'JPY');

-- ROUTE_PROPOSED な貨物に対応する leg（経路区間）データ
-- DEMO0000004D: JPTYO -> CNSHA (V0100 直行便)
INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0100', 'JPTYO', 'CNSHA',
       '2030-01-01 08:00:00', '2030-01-04 18:00:00', 1
FROM cargo WHERE booking_id = 'DEMO0000004D';

-- DEMO0000005E: CNSHA -> USLAX (V0500 1区間目)
INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0500', 'CNSHA', 'USLAX',
       '2030-01-05 08:00:00', '2030-01-19 18:00:00', 1
FROM cargo WHERE booking_id = 'DEMO0000005E';

-- CONFIRMED な貨物に対応する leg（経路区間）データ
-- DEMO0000006F: JPTYO -> CNSHA (V0100 直行便)
INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0100', 'JPTYO', 'CNSHA',
       '2030-01-01 08:00:00', '2030-01-04 18:00:00', 1
FROM cargo WHERE booking_id = 'DEMO0000006F';

-- DEMO0000007G: JPTYO -> KRPUS (V0300 1区間目)
INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0300', 'JPTYO', 'KRPUS',
       '2030-01-02 08:00:00', '2030-01-03 18:00:00', 1
FROM cargo WHERE booking_id = 'DEMO0000007G';

-- DEMO0000008H: CNSHA -> SGSIN (V0200 経由 CNHKG -> SGSIN の2区間)
INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0200', 'CNSHA', 'CNHKG',
       '2030-01-05 08:00:00', '2030-01-07 18:00:00', 1
FROM cargo WHERE booking_id = 'DEMO0000008H';

INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number)
SELECT id, 'V0200', 'CNHKG', 'SGSIN',
       '2030-01-08 08:00:00', '2030-01-11 18:00:00', 2
FROM cargo WHERE booking_id = 'DEMO0000008H';
