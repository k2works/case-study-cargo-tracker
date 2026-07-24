-- name: InsertShipper :one
INSERT INTO shipper (
    shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8
)
RETURNING id;

-- name: ExistsShipperByEmail :one
SELECT EXISTS (
    SELECT 1 FROM shipper WHERE email = $1
) AS exists;

-- name: FindShipperByCode :one
SELECT id, shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate, created_at, updated_at
FROM shipper
WHERE shipper_code = $1;

-- name: ListShippers :many
SELECT id, shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate, created_at, updated_at
FROM shipper
ORDER BY id DESC;

-- name: ExistsShipperByCode :one
SELECT EXISTS (
    SELECT 1 FROM shipper WHERE shipper_code = $1
) AS exists;
