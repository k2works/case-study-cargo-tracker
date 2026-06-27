-- migrate:up

-- IT2 US01 経路候補 (Estimate 集約配下のエンティティ)
-- voyage_numbers はカンマ区切り Text で保持 (cross-BC ACL 尊重、
-- Routing.voyage への FK は張らない)。

CREATE TABLE route_candidate (
    id              BIGSERIAL PRIMARY KEY,
    estimate_id     BIGINT NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    rank            INTEGER NOT NULL,
    transit_days    INTEGER NOT NULL,
    estimated_cost  NUMERIC NOT NULL,
    voyage_numbers  TEXT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT route_candidate_rank_unique UNIQUE (estimate_id, rank),
    CONSTRAINT route_candidate_rank_nonneg
        CHECK (rank >= 0),
    CONSTRAINT route_candidate_transit_positive
        CHECK (transit_days >= 1),
    CONSTRAINT route_candidate_cost_nonneg
        CHECK (estimated_cost >= 0)
);

CREATE INDEX route_candidate_estimate_id_idx ON route_candidate (estimate_id);

-- migrate:down

DROP INDEX IF EXISTS route_candidate_estimate_id_idx;
DROP TABLE route_candidate;
