-- Booking Microservice: cargo テーブル作成
CREATE TABLE IF NOT EXISTS cargo (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id                VARCHAR(20)   NOT NULL UNIQUE,
    shipper_id                BIGINT        NOT NULL,
    booking_status            VARCHAR(30)   NOT NULL DEFAULT 'PRELIMINARY',
    transport_status          VARCHAR(30)   NOT NULL DEFAULT 'NOT_RECEIVED',
    routing_status            VARCHAR(30)   NOT NULL DEFAULT 'NOT_ROUTED',
    cargo_type                VARCHAR(20)   NOT NULL DEFAULT 'GENERAL',
    weight_kg                 NUMERIC(10,3) NOT NULL,
    spec_origin_unlocode      VARCHAR(5),
    spec_destination_unlocode VARCHAR(5),
    spec_arrival_deadline     DATE,
    booking_amount_value      INTEGER       NOT NULL DEFAULT 0,
    booking_amount_currency   VARCHAR(3)    NOT NULL DEFAULT 'JPY',
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
