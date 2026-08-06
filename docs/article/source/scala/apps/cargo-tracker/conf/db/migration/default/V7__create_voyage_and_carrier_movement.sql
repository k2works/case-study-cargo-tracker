-- US24/US25 Routing Context（航海スケジュール管理）
-- data-model.md 準拠: サロゲートキー BIGSERIAL + 業務キー UNIQUE、FK は id 参照

CREATE TABLE voyage (
    id             BIGSERIAL PRIMARY KEY,
    voyage_number  VARCHAR(20) NOT NULL UNIQUE,
    version        INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE carrier_movement (
    id                          BIGSERIAL PRIMARY KEY,
    voyage_id                   BIGINT      NOT NULL REFERENCES voyage(id) ON DELETE CASCADE,
    departure_location_unlocode VARCHAR(5)  NOT NULL,
    arrival_location_unlocode   VARCHAR(5)  NOT NULL,
    departure_date              TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_date                TIMESTAMP WITH TIME ZONE NOT NULL,
    seq_number                  INTEGER     NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (voyage_id, seq_number),
    CONSTRAINT cm_arrival_after_departure CHECK (arrival_date > departure_date),
    CONSTRAINT cm_origin_differs CHECK (departure_location_unlocode <> arrival_location_unlocode)
);

CREATE INDEX idx_cm_voyage ON carrier_movement(voyage_id);
CREATE INDEX idx_cm_departure ON carrier_movement(departure_location_unlocode);
CREATE INDEX idx_cm_arrival ON carrier_movement(arrival_location_unlocode);
