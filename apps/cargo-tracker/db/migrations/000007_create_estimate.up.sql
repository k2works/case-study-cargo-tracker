-- Estimation Context: 輸送見積（estimate）とルート候補（route_candidate）。
CREATE TABLE estimate (
    id               BIGSERIAL PRIMARY KEY,
    estimate_id      UUID         NOT NULL UNIQUE,
    origin_unlocode  VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5) NOT NULL,
    arrival_deadline DATE,
    cargo_type       VARCHAR(20)  NOT NULL,
    weight_kg        NUMERIC(10, 3) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE route_candidate (
    id             BIGSERIAL PRIMARY KEY,
    estimate_id    BIGINT       NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number  VARCHAR(20)  NOT NULL,
    transit_days   INTEGER      NOT NULL,
    estimated_cost BIGINT       NOT NULL,
    seq_number     INTEGER      NOT NULL
);

CREATE INDEX idx_route_candidate_estimate ON route_candidate(estimate_id, seq_number);
