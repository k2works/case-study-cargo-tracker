-- name: ExistsShipperByCode :one
-- Booking Context の ACL 用: 荷主コードで荷主の存在を確認する（読み取りのみ）。
SELECT EXISTS (
    SELECT 1 FROM shipper WHERE shipper_code = $1
);
