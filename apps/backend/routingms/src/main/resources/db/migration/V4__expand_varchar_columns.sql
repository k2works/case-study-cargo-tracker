ALTER TABLE voyage ALTER COLUMN carrier_code TYPE VARCHAR(50);
ALTER TABLE voyage ALTER COLUMN origin_unlocode TYPE VARCHAR(10);
ALTER TABLE voyage ALTER COLUMN dest_unlocode TYPE VARCHAR(10);
ALTER TABLE carrier_movement ALTER COLUMN departure_unlocode TYPE VARCHAR(10);
ALTER TABLE carrier_movement ALTER COLUMN arrival_unlocode TYPE VARCHAR(10);
