ALTER TABLE tracking_exception_event
    DROP COLUMN IF EXISTS resolution_notes,
    DROP COLUMN IF EXISTS location_unlocode;
