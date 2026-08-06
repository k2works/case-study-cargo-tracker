-- name: InsertNotification :exec
INSERT INTO notification (cargo_id, shipper_code, summary, sent_at)
VALUES ($1, $2, $3, $4);

-- name: ListNotificationsByBookingId :many
SELECT n.shipper_code, n.summary, n.sent_at
FROM notification n
JOIN cargo c ON c.id = n.cargo_id
WHERE c.booking_id = $1
ORDER BY n.sent_at DESC;
