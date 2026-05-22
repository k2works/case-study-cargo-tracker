ALTER TABLE voyage_accepted_cargo_type DROP CONSTRAINT voyage_accepted_cargo_type_voyage_number_fkey;
ALTER TABLE carrier_movement DROP CONSTRAINT carrier_movement_voyage_number_fkey;

ALTER TABLE voyage ALTER COLUMN voyage_number TYPE VARCHAR(50);
ALTER TABLE carrier_movement ALTER COLUMN voyage_number TYPE VARCHAR(50);
ALTER TABLE voyage_accepted_cargo_type ALTER COLUMN voyage_number TYPE VARCHAR(50);

ALTER TABLE carrier_movement
    ADD CONSTRAINT carrier_movement_voyage_number_fkey
        FOREIGN KEY (voyage_number) REFERENCES voyage(voyage_number);

ALTER TABLE voyage_accepted_cargo_type
    ADD CONSTRAINT voyage_accepted_cargo_type_voyage_number_fkey
        FOREIGN KEY (voyage_number) REFERENCES voyage(voyage_number);
