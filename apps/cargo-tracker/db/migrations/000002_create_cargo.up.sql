-- Booking Context: 貨物予約テーブル
-- 荷主参照は BC 独立性のため業務識別子 shipper_code で保持する（数値 FK は用いない）。
CREATE TABLE cargo (
    id                        BIGSERIAL PRIMARY KEY,
    booking_id                VARCHAR(20)  NOT NULL UNIQUE,
    shipper_code              VARCHAR(20)  NOT NULL,
    booking_status            VARCHAR(30)  NOT NULL,
    cargo_type                VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    weight_kg                 NUMERIC(10, 3) NOT NULL,
    spec_origin_unlocode      VARCHAR(5)   NOT NULL,
    spec_destination_unlocode VARCHAR(5)   NOT NULL,
    spec_arrival_deadline     DATE,
    booking_amount_value      BIGINT       NOT NULL DEFAULT 0,
    booking_amount_currency   VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW()
);
