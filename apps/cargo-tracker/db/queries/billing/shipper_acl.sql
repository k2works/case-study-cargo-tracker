-- name: GetShipperContract :one
-- Billing → Shipper の ACL（業務識別子 shipper_code で契約割引率を直読・ADR-0005 先例）。
SELECT shipper_type, discount_rate
FROM shipper WHERE shipper_code = $1;
