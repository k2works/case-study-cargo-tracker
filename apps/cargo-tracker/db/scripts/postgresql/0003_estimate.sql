-- estimate / route_candidate（見積・US01）。PostgreSQL 方言。
CREATE TABLE estimate (
    id                   BIGSERIAL PRIMARY KEY,
    estimate_id          UUID         NOT NULL UNIQUE,
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    arrival_deadline     DATE         NOT NULL,
    cargo_type           VARCHAR(30)  NOT NULL,
    weight_kg            NUMERIC(10,3) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE route_candidate (
    id             BIGSERIAL PRIMARY KEY,
    estimate_id    BIGINT      NOT NULL REFERENCES estimate(id),
    voyage_number  VARCHAR(20) NOT NULL,
    transit_port   VARCHAR(5),
    transit_days   INTEGER     NOT NULL,
    estimated_cost NUMERIC(12,2) NOT NULL,
    rank           INTEGER     NOT NULL DEFAULT 0
);
