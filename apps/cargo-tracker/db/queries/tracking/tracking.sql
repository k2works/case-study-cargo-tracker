-- name: InsertTrackingActivity :one
INSERT INTO tracking_activity (tracking_number, booking_id, transport_status)
VALUES ($1, $2, $3)
RETURNING id;

-- name: UpdateTrackingTransportStatus :exec
UPDATE tracking_activity SET transport_status = $2, updated_at = NOW() WHERE tracking_number = $1;

-- name: GetTrackingActivityByNumber :one
SELECT id, tracking_number, booking_id, transport_status
FROM tracking_activity WHERE tracking_number = $1;

-- name: GetTrackingActivityByBookingId :one
SELECT id, tracking_number, booking_id, transport_status
FROM tracking_activity WHERE booking_id = $1;

-- name: InsertTrackingHandlingEvent :exec
INSERT INTO tracking_handling_event (tracking_id, event_type, transport_status, event_time, location_unlocode, voyage_number, seq_number)
VALUES ($1, $2, $3, $4, $5, $6, $7);

-- name: ListTrackingHandlingEvents :many
SELECT event_type, transport_status, event_time, location_unlocode, voyage_number
FROM tracking_handling_event WHERE tracking_id = $1 ORDER BY seq_number;

-- name: CountTrackingHandlingEvents :one
SELECT COUNT(*) FROM tracking_handling_event WHERE tracking_id = $1;

-- name: CountTrackingActivitiesOnDate :one
SELECT COUNT(*) FROM tracking_activity WHERE tracking_number LIKE $1;
