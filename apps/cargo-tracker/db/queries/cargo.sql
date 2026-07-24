-- name: InsertCargo :one
INSERT INTO cargo (
    booking_id, shipper_code, booking_status, cargo_type, weight_kg,
    spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
    booking_amount_value, booking_amount_currency
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
)
RETURNING id;

-- name: ListCargos :many
SELECT id, booking_id, shipper_code, booking_status, cargo_type, weight_kg,
       spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
       booking_amount_value, booking_amount_currency, created_at, updated_at
FROM cargo
ORDER BY id DESC;
