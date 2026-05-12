-- V7: estimate / route_candidate テーブル作成
CREATE TABLE estimate (
  id BIGSERIAL PRIMARY KEY,
  estimate_id UUID NOT NULL UNIQUE,
  origin_unlocode VARCHAR(5) NOT NULL,
  destination_unlocode VARCHAR(5) NOT NULL,
  arrival_deadline DATE NOT NULL,
  cargo_type VARCHAR(30) NOT NULL,
  weight_kg NUMERIC(10,3) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE route_candidate (
  id BIGSERIAL PRIMARY KEY,
  estimate_id BIGINT NOT NULL REFERENCES estimate(id),
  voyage_number VARCHAR(20) NOT NULL,
  transit_port VARCHAR(5),
  transit_days INT NOT NULL,
  estimated_cost NUMERIC(12,2) NOT NULL,
  rank INT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
