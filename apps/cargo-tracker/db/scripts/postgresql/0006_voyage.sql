-- voyage / carrier_movement（航海スケジュール・US24/US25/US07/US08）。PostgreSQL 方言。
-- voyage は船名・運送会社・対応貨物種別を保持する（US24・ADR: data-model 拡張）。
CREATE TABLE voyage (
    id                    BIGSERIAL PRIMARY KEY,
    voyage_number         VARCHAR(20)  NOT NULL UNIQUE,
    vessel_name           VARCHAR(100) NOT NULL,
    carrier_name          VARCHAR(100) NOT NULL,
    supported_cargo_types VARCHAR(50)  NOT NULL,  -- GENERAL,HAZARDOUS,REFRIGERATED のカンマ区切り
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE carrier_movement (
    id                          BIGSERIAL PRIMARY KEY,
    voyage_id                   BIGINT      NOT NULL REFERENCES voyage(id),
    departure_location_unlocode VARCHAR(5)  NOT NULL,
    arrival_location_unlocode   VARCHAR(5)  NOT NULL,
    departure_date              TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_date                TIMESTAMP WITH TIME ZONE NOT NULL,
    seq_number                  INTEGER     NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
