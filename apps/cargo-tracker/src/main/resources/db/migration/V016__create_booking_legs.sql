-- V016: booking_legs テーブルを追加する
-- 予約の航海区間詳細（出発港・到着港・出発日・到着日）を永続化する
-- bookings.id（UUID）を FK とし、V014 の voyage_legs と一貫した命名規則を使用

CREATE TABLE booking_legs (
    id                  BIGSERIAL       PRIMARY KEY,
    booking_id          UUID            NOT NULL,
    voyage_number       VARCHAR(20)     NOT NULL,
    origin_locode       VARCHAR(5)      NOT NULL,
    destination_locode  VARCHAR(5)      NOT NULL,
    departure_date      DATE            NOT NULL,
    arrival_date        DATE            NOT NULL,
    leg_order           INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_legs_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX idx_booking_legs_booking_id ON booking_legs(booking_id);
