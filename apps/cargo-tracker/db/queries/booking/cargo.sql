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
       routing_status,
       created_at, updated_at
FROM cargo
ORDER BY id DESC;

-- name: GetCargoByBookingId :one
SELECT id, booking_id, shipper_code, booking_status, cargo_type, weight_kg,
       spec_origin_unlocode, spec_destination_unlocode, spec_arrival_deadline,
       booking_amount_value, booking_amount_currency,
       hazardous_class, un_number, proper_shipping_name,
       min_temperature, max_temperature, temperature_unit,
       routing_status,
       created_at, updated_at
FROM cargo
WHERE booking_id = $1;

-- name: UpdateCargoStatus :execrows
UPDATE cargo
SET booking_status = $2,
    updated_at = NOW()
WHERE booking_id = $1;

-- name: UpdateCargoRoutingStatus :execrows
UPDATE cargo
SET routing_status = $2,
    updated_at = NOW()
WHERE booking_id = $1;

-- name: DeleteLegsByCargoId :exec
DELETE FROM leg WHERE cargo_id = $1;

-- name: InsertLeg :exec
INSERT INTO leg (
    cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
    load_time, unload_time, seq_number
) VALUES (
    $1, $2, $3, $4, $5, $6, $7
);

-- name: ListLegsByBookingId :many
SELECT l.voyage_number, l.load_location_unlocode, l.unload_location_unlocode,
       l.load_time, l.unload_time, l.seq_number
FROM leg l
JOIN cargo c ON c.id = l.cargo_id
WHERE c.booking_id = $1
ORDER BY l.seq_number;

-- name: GetCargoIdByBookingId :one
SELECT id FROM cargo WHERE booking_id = $1;

-- name: UpdateCargoTracking :execrows
UPDATE cargo
SET booking_status = $2,
    transport_status = $3,
    tracking_number = $4,
    updated_at = NOW()
WHERE booking_id = $1;
