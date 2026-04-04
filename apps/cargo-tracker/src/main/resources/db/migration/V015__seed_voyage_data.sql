-- V015: 航海スケジュールのシードデータ
-- StubRouteProviderAdapter で定義済みの航海をベースに実データを投入する

-- SG001: 東京 → シンガポール（直行）
INSERT INTO voyages (voyage_number, carrier_name, supported_cargo_types)
VALUES ('SG001', 'Japan Pacific Lines', 'GENERAL,REFRIGERATED');

INSERT INTO voyage_legs (voyage_number, origin_locode, destination_locode, departure_date, arrival_date, leg_order)
VALUES ('SG001', 'JPTYO', 'SGSIN', '2026-06-01', '2026-06-15', 0);

-- SG002: 東京 → 釜山 → シンガポール（1 回乗り継ぎ）
INSERT INTO voyages (voyage_number, carrier_name, supported_cargo_types)
VALUES ('SG002', 'Korea Shipping Corp', 'GENERAL,HAZARDOUS');

INSERT INTO voyage_legs (voyage_number, origin_locode, destination_locode, departure_date, arrival_date, leg_order)
VALUES ('SG002', 'JPTYO', 'KRPUS', '2026-06-01', '2026-06-05', 0);

INSERT INTO voyage_legs (voyage_number, origin_locode, destination_locode, departure_date, arrival_date, leg_order)
VALUES ('SG002', 'KRPUS', 'SGSIN', '2026-06-07', '2026-06-19', 1);

-- SG003: 東京 → シンガポール（直行・冷凍専用）
INSERT INTO voyages (voyage_number, carrier_name, supported_cargo_types)
VALUES ('SG003', 'Cold Chain Carriers', 'GENERAL,REFRIGERATED');

INSERT INTO voyage_legs (voyage_number, origin_locode, destination_locode, departure_date, arrival_date, leg_order)
VALUES ('SG003', 'JPTYO', 'SGSIN', '2026-06-10', '2026-06-28', 0);

-- SG004: 大阪 → シンガポール（直行）
INSERT INTO voyages (voyage_number, carrier_name, supported_cargo_types)
VALUES ('SG004', 'Osaka International Freight', 'GENERAL');

INSERT INTO voyage_legs (voyage_number, origin_locode, destination_locode, departure_date, arrival_date, leg_order)
VALUES ('SG004', 'JPOSA', 'SGSIN', '2026-06-05', '2026-06-18', 0);
