-- Shipper aggregate (US02, US03)
-- See domain-model.md / data-model.md

CREATE TABLE shipper (
    id BIGSERIAL PRIMARY KEY,
    shipper_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address VARCHAR(500) NOT NULL,
    shipper_type VARCHAR(20) NOT NULL,
    contract_number VARCHAR(50),
    discount_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shipper_email ON shipper(email);
CREATE INDEX idx_shipper_type ON shipper(shipper_type);
