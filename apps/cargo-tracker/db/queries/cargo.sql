-- name: InsertCargo :one
INSERT INTO cargo (
    booking_id, shipper_code, booking_status, cargo_type, weight_kg,
    spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
    booking_amount_value, booking_amount_currency,
    hazardous_class, un_number, proper_shipping_name,
    min_temperature, max_temperature, temperature_unit
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
    $11, $12, $13, $14, $15, $16
)
RETURNING id;

-- name: ListCargos :many
SELECT id, booking_id, shipper_code, booking_status, cargo_type, weight_kg,
       spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
       booking_amount_value, booking_amount_currency,
       hazardous_class, un_number, proper_shipping_name,
       min_temperature, max_temperature, temperature_unit,
       created_at, updated_at
FROM cargo
ORDER BY id DESC;

-- name: GetCargoByBookingId :one
SELECT id, booking_id, shipper_code, booking_status, cargo_type, weight_kg,
       spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
       booking_amount_value, booking_amount_currency,
       hazardous_class, un_number, proper_shipping_name,
       min_temperature, max_temperature, temperature_unit,
       created_at, updated_at
FROM cargo
WHERE booking_id = $1;

-- name: UpdateCargoStatus :execrows
UPDATE cargo
SET booking_status = $2,
    updated_at = NOW()
WHERE booking_id = $1;
