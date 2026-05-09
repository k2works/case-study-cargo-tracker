-- Seed voyage and carrier_movement data for E2E testing and demo
-- Voyages covering key routes (JPTYO <-> CNSHA, etc.)

INSERT INTO voyage (voyage_number) VALUES
    ('V0100'),
    ('V0200'),
    ('V0300'),
    ('V0400'),
    ('V0500');

-- V0100: Tokyo -> Shanghai (direct)
INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number)
VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0100'),
     'JPTYO', 'CNSHA',
     '2030-01-01 08:00:00+00', '2030-01-04 18:00:00+00', 1);

-- V0200: Shanghai -> Hong Kong -> Singapore
INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number)
VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0200'),
     'CNSHA', 'CNHKG',
     '2030-01-05 08:00:00+00', '2030-01-07 18:00:00+00', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0200'),
     'CNHKG', 'SGSIN',
     '2030-01-08 08:00:00+00', '2030-01-11 18:00:00+00', 2);

-- V0300: Tokyo -> Busan -> Shanghai (connecting via Busan)
INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number)
VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0300'),
     'JPTYO', 'KRPUS',
     '2030-01-02 08:00:00+00', '2030-01-03 18:00:00+00', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0300'),
     'KRPUS', 'CNSHA',
     '2030-01-04 08:00:00+00', '2030-01-06 18:00:00+00', 2);

-- V0400: Singapore -> Rotterdam
INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number)
VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0400'),
     'SGSIN', 'NLRTM',
     '2030-01-12 08:00:00+00', '2030-01-30 18:00:00+00', 1);

-- V0500: Shanghai -> Los Angeles -> New York
INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number)
VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0500'),
     'CNSHA', 'USLAX',
     '2030-01-05 08:00:00+00', '2030-01-19 18:00:00+00', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0500'),
     'USLAX', 'USNYC',
     '2030-01-20 08:00:00+00', '2030-01-25 18:00:00+00', 2);
