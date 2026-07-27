-- name: InsertInvoice :exec
INSERT INTO invoice (
    invoice_number, booking_id, shipper_code, shipper_type,
    base_amount_value, discount_rate, discount_amount_value, tax_amount_value, total_amount_value,
    currency, payment_status, issued_at, due_date, paid_at
) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
ON CONFLICT (invoice_number) DO UPDATE SET
    payment_status = EXCLUDED.payment_status,
    paid_at = EXCLUDED.paid_at,
    updated_at = NOW();

-- name: GetInvoiceByNumber :one
SELECT invoice_number, booking_id, shipper_code, shipper_type,
       base_amount_value, discount_rate, discount_amount_value, tax_amount_value, total_amount_value,
       currency, payment_status, issued_at, due_date, paid_at
FROM invoice WHERE invoice_number = $1;

-- name: ExistsInvoiceByBookingId :one
SELECT EXISTS(SELECT 1 FROM invoice WHERE booking_id = $1);

-- name: ListInvoices :many
SELECT invoice_number, booking_id, shipper_code, shipper_type,
       base_amount_value, discount_rate, discount_amount_value, tax_amount_value, total_amount_value,
       currency, payment_status, issued_at, due_date, paid_at
FROM invoice ORDER BY issued_at DESC, id DESC;

-- name: NextInvoiceSequence :one
INSERT INTO sequence_counter (name, day, value)
VALUES ($1, $2, 1)
ON CONFLICT (name, day) DO UPDATE SET value = sequence_counter.value + 1
RETURNING value;
