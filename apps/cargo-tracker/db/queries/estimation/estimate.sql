-- name: InsertEstimate :one
INSERT INTO estimate (estimate_id, origin_unlocode, destination_unlocode, arrival_deadline, cargo_type, weight_kg, status)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING id;

-- name: InsertRouteCandidate :exec
INSERT INTO route_candidate (estimate_id, voyage_number, transit_days, estimated_cost, seq_number)
VALUES ($1, $2, $3, $4, $5);

-- name: GetEstimateByEstimateId :one
SELECT id, estimate_id, origin_unlocode, destination_unlocode, arrival_deadline, cargo_type, weight_kg, status
FROM estimate WHERE estimate_id = $1;

-- name: ListRouteCandidates :many
SELECT voyage_number, transit_days, estimated_cost
FROM route_candidate WHERE estimate_id = $1 ORDER BY seq_number;

-- name: ListEstimates :many
SELECT id, estimate_id, origin_unlocode, destination_unlocode, arrival_deadline, cargo_type, weight_kg, status
FROM estimate ORDER BY created_at DESC;
