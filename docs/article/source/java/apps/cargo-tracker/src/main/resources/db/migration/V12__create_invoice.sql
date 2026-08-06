CREATE TABLE invoice (
    id                     BIGSERIAL PRIMARY KEY,
    invoice_number         VARCHAR(30)    NOT NULL,
    booking_id             VARCHAR(100)   NOT NULL,
    total_amount_value     INT            NOT NULL,
    discount_type          VARCHAR(20)    NOT NULL DEFAULT 'NONE',
    discount_rate          DECIMAL(5, 4)  NOT NULL DEFAULT 0,
    discounted_amount_value INT           NOT NULL,
    payment_status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    due_date               DATE           NOT NULL,
    created_at             TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_invoice_number   UNIQUE (invoice_number),
    CONSTRAINT uk_invoice_booking  UNIQUE (booking_id)
);
