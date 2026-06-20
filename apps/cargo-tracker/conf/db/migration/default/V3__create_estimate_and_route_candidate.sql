-- Estimation context (US01)
-- See domain-model.md (Estimation Context) / data-model.md

CREATE TABLE estimate (
    id BIGSERIAL PRIMARY KEY,
    estimate_id VARCHAR(36) NOT NULL UNIQUE,
    origin_unlocode VARCHAR(5) NOT NULL,
    destination_unlocode VARCHAR(5) NOT NULL,
    deadline DATE NOT NULL,
    cargo_type VARCHAR(50) NOT NULL,
    weight_kg BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE route_candidate (
    id BIGSERIAL PRIMARY KEY,
    estimate_id BIGINT NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number VARCHAR(20) NOT NULL,
    transit_ports VARCHAR(500) NOT NULL,
    transit_days INTEGER NOT NULL,
    estimated_cost_amount BIGINT NOT NULL,
    estimated_cost_currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_estimate_status ON estimate(status);
CREATE INDEX idx_route_candidate_estimate ON route_candidate(estimate_id);
