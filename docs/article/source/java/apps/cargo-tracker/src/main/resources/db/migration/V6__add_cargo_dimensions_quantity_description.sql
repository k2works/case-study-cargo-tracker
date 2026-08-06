ALTER TABLE cargo ADD COLUMN dimension_length NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN dimension_width NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN dimension_height NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN quantity INTEGER;
ALTER TABLE cargo ADD COLUMN description VARCHAR(500);
