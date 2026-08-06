CREATE TABLE tracking_record (
    id                  BIGSERIAL PRIMARY KEY,
    tracking_number     VARCHAR(30)   NOT NULL,
    booking_id          VARCHAR(100)  NOT NULL,
    cargo_status        VARCHAR(30)   NOT NULL DEFAULT 'AWAITING_RECEIPT',
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tracking_number UNIQUE (tracking_number),
    CONSTRAINT uk_tracking_booking UNIQUE (booking_id)
);

ALTER TABLE cargo ADD COLUMN tracking_number VARCHAR(30);
