-- name: InsertVoyage :one
INSERT INTO voyage (voyage_number, vessel_name, carrier, supported_cargo_types)
VALUES ($1, $2, $3, $4)
RETURNING id;

-- name: InsertCarrierMovement :exec
INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number
) VALUES ($1, $2, $3, $4, $5, $6);

-- name: ExistsVoyageByNumber :one
SELECT EXISTS (SELECT 1 FROM voyage WHERE voyage_number = $1);

-- name: GetVoyageByNumber :one
SELECT id, voyage_number, vessel_name, carrier, supported_cargo_types
FROM voyage WHERE voyage_number = $1;

-- name: ListCarrierMovements :many
SELECT departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number
FROM carrier_movement WHERE voyage_id = $1 ORDER BY seq_number;

-- name: ListVoyages :many
SELECT id, voyage_number, vessel_name, carrier, supported_cargo_types
FROM voyage ORDER BY voyage_number;

-- name: UpdateVoyage :exec
UPDATE voyage
SET vessel_name = $2, carrier = $3, supported_cargo_types = $4, updated_at = NOW()
WHERE voyage_number = $1;

-- name: DeleteCarrierMovements :exec
DELETE FROM carrier_movement WHERE voyage_id = $1;
