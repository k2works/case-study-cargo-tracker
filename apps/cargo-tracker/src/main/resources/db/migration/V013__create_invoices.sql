CREATE TABLE invoices (
    id                  VARCHAR(36)     PRIMARY KEY,
    booking_id          VARCHAR(36)     NOT NULL,
    freight_charge_id   VARCHAR(36)     NOT NULL,
    amount              DECIMAL(15,2)   NOT NULL,
    due_date            DATE            NOT NULL,
    payment_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL
);
CREATE INDEX idx_invoices_booking_id ON invoices(booking_id);
CREATE INDEX idx_invoices_freight_charge_id ON invoices(freight_charge_id);
