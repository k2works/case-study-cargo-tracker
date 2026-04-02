ALTER TABLE bookings ADD COLUMN cargo_un_number        VARCHAR(20);
ALTER TABLE bookings ADD COLUMN cargo_hazard_class     VARCHAR(50);
ALTER TABLE bookings ADD COLUMN cargo_min_temp_celsius NUMERIC(5, 1);
ALTER TABLE bookings ADD COLUMN cargo_max_temp_celsius NUMERIC(5, 1);
