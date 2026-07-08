-- 見積・ルート候補テーブル（PostgreSQL 方言・US01・data-model）
CREATE TABLE estimate (
    id                   BIGSERIAL    PRIMARY KEY,
    estimate_id          UUID         NOT NULL,
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    arrival_deadline     DATE         NOT NULL,
    cargo_type           VARCHAR(30)  NOT NULL,
    weight_kg            NUMERIC(10,3) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_estimate_id UNIQUE (estimate_id)
);

CREATE TABLE route_candidate (
    id             BIGSERIAL    PRIMARY KEY,
    estimate_id    BIGINT       NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number  VARCHAR(20)  NOT NULL,
    transit_port   VARCHAR(5),
    transit_days   INT          NOT NULL,
    estimated_cost NUMERIC(12,2) NOT NULL,
    rank           INT          NOT NULL DEFAULT 0
);
