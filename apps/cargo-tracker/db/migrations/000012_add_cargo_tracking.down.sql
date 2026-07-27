DROP INDEX IF EXISTS idx_cargo_tracking_number;
ALTER TABLE cargo
    DROP COLUMN IF EXISTS transport_status,
    DROP COLUMN IF EXISTS tracking_number;
