-- migrate:up

-- IT1 US24 航海集約
CREATE TABLE voyage (
    id              BIGSERIAL PRIMARY KEY,
    voyage_number   VARCHAR(20) NOT NULL UNIQUE,
    version         BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 航海の連続区間。voyage への FK と、各区間の出発港・到着港 location FK を持つ。
-- seq_number で集約内の順序を保持 (mkVoyage の連続性検証はアプリ側で行う)
CREATE TABLE carrier_movement (
    id                              BIGSERIAL PRIMARY KEY,
    voyage_id                       BIGINT NOT NULL REFERENCES voyage(id) ON DELETE CASCADE,
    seq_number                      INTEGER NOT NULL,
    departure_location_unlocode     VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    arrival_location_unlocode       VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    departure_time                  TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_time                    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT carrier_movement_seq_unique UNIQUE (voyage_id, seq_number),
    CONSTRAINT carrier_movement_time_order
        CHECK (departure_time < arrival_time)
);

CREATE INDEX carrier_movement_voyage_id_idx ON carrier_movement (voyage_id);

-- migrate:down

DROP TABLE carrier_movement;
DROP TABLE voyage;
