-- 航海スケジュールテーブル（PostgreSQL 方言・US24・data-model IT3 実装状況）
CREATE TABLE voyage (
    id                         BIGSERIAL    PRIMARY KEY,
    voyage_number              VARCHAR(20)  NOT NULL,
    vessel_name                VARCHAR(200) NOT NULL,
    carrier                    VARCHAR(200) NOT NULL,
    supported_cargo_types      VARCHAR(100) NOT NULL,
    created_at                 TIMESTAMP    NOT NULL,
    updated_at                 TIMESTAMP    NOT NULL,
    version                    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_voyage_voyage_number UNIQUE (voyage_number)
);

CREATE TABLE carrier_movement (
    id                            BIGSERIAL   PRIMARY KEY,
    voyage_id                     BIGINT      NOT NULL REFERENCES voyage(id),
    departure_location_unlocode   VARCHAR(5)  NOT NULL,
    arrival_location_unlocode     VARCHAR(5)  NOT NULL,
    departure_date                TIMESTAMP   NOT NULL,
    arrival_date                  TIMESTAMP   NOT NULL,
    seq_number                    INTEGER     NOT NULL,
    created_at                    TIMESTAMP   NOT NULL,
    updated_at                    TIMESTAMP   NOT NULL,
    CONSTRAINT uk_carrier_movement_voyage_seq UNIQUE (voyage_id, seq_number),
    CONSTRAINT ck_carrier_movement_seq_positive CHECK (seq_number >= 1)
);
