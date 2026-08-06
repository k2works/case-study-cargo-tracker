-- Routing Context: 航海スケジュール（voyage）と運送区間（carrier_movement）。
-- US24 の受入基準に合わせ vessel_name/carrier/supported_cargo_types を保持する（ADR-0006）。
CREATE TABLE voyage (
    id                    BIGSERIAL PRIMARY KEY,
    voyage_number         VARCHAR(20)  NOT NULL UNIQUE,
    vessel_name           VARCHAR(100) NOT NULL,
    carrier               VARCHAR(100) NOT NULL,
    supported_cargo_types VARCHAR(100) NOT NULL,  -- CSV 形式（例: GENERAL,REFRIGERATED）
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 運送区間。BC 独立性のため location への FK 制約は設けず UN/LOCODE 文字列で保持する。
CREATE TABLE carrier_movement (
    id                          BIGSERIAL PRIMARY KEY,
    voyage_id                   BIGINT       NOT NULL REFERENCES voyage(id) ON DELETE CASCADE,
    departure_location_unlocode VARCHAR(5)   NOT NULL,
    arrival_location_unlocode   VARCHAR(5)   NOT NULL,
    departure_date              TIMESTAMP    NOT NULL,
    arrival_date                TIMESTAMP    NOT NULL,
    seq_number                  INTEGER      NOT NULL,
    created_at                  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_carrier_movement_voyage ON carrier_movement(voyage_id, seq_number);
