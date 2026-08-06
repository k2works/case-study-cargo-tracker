-- Booking context (US04)
-- See domain-model.md (Booking Context) / data-model.md

CREATE TABLE cargo (
    id BIGSERIAL PRIMARY KEY,
    tracking_id VARCHAR(20) NOT NULL UNIQUE,
    shipper_code VARCHAR(20) NOT NULL,
    origin_unlocode VARCHAR(5) NOT NULL,
    destination_unlocode VARCHAR(5) NOT NULL,
    arrival_deadline DATE NOT NULL,
    cargo_type VARCHAR(50) NOT NULL,
    weight_kg BIGINT NOT NULL,
    description VARCHAR(500),
    quantity INTEGER,
    hazardous_class VARCHAR(50),
    hazardous_un_number VARCHAR(20),
    hazardous_proper_name VARCHAR(200),
    booking_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cargo_shipper ON cargo(shipper_code);
CREATE INDEX idx_cargo_status ON cargo(booking_status);
