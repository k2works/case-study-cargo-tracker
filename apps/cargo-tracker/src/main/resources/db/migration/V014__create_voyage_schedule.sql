-- V014: 航海スケジュール（voyage・voyage_legs）テーブルを追加する
-- voyage: 航海情報（航海番号・運送会社・対応貨物種別）
-- voyage_legs: 航海区間（出発港・到着港・出発日・到着日）

CREATE TABLE voyages (
    voyage_number           VARCHAR(20)     PRIMARY KEY,
    carrier_name            VARCHAR(100)    NOT NULL,
    supported_cargo_types   VARCHAR(100)    NOT NULL  -- カンマ区切り例: "GENERAL,REFRIGERATED"
);

CREATE TABLE voyage_legs (
    id                  BIGSERIAL       PRIMARY KEY,
    voyage_number       VARCHAR(20)     NOT NULL,
    origin_locode       VARCHAR(5)      NOT NULL,
    destination_locode  VARCHAR(5)      NOT NULL,
    departure_date      DATE            NOT NULL,
    arrival_date        DATE            NOT NULL,
    leg_order           INT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_voyage_legs_voyage FOREIGN KEY (voyage_number) REFERENCES voyages(voyage_number)
);

CREATE INDEX idx_voyage_legs_voyage_number ON voyage_legs(voyage_number);
CREATE INDEX idx_voyage_legs_origin ON voyage_legs(origin_locode, departure_date);
CREATE INDEX idx_voyage_legs_dest ON voyage_legs(destination_locode, arrival_date);
