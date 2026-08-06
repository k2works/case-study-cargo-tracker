-- name: InsertHandlingActivity :one
INSERT INTO handling_activity (booking_id, event_type, event_completion_time, location_unlocode, voyage_number, consignee_confirmation, operator_name)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING id;

-- name: ListHandlingActivitiesByBookingId :many
SELECT booking_id, event_type, event_completion_time, location_unlocode, voyage_number, consignee_confirmation, operator_name
FROM handling_activity WHERE booking_id = $1 ORDER BY event_completion_time;
