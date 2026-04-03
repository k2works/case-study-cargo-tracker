CREATE TABLE freight_charges (
    id              VARCHAR(36)     PRIMARY KEY,
    booking_id      VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    base_amount     DECIMAL(15,2)   NOT NULL,
    adjustment_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount    DECIMAL(15,2)   NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL
);

CREATE INDEX idx_freight_charges_booking_id ON freight_charges(booking_id);
