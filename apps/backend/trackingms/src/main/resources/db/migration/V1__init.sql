-- trackingms 初期スキーマ
CREATE TABLE IF NOT EXISTS tracking_activity (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL,
    booking_id       VARCHAR(20) NOT NULL,
    transport_status VARCHAR(30) NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tracking_number UNIQUE (tracking_number),
    CONSTRAINT uk_booking_id UNIQUE (booking_id)
);

CREATE TABLE IF NOT EXISTS tracking_handling_event (
    id                BIGSERIAL PRIMARY KEY,
    tracking_id       BIGINT      NOT NULL,
    event_type        VARCHAR(30) NOT NULL,
    event_time        TIMESTAMP   NOT NULL,
    location_unlocode VARCHAR(5),
    voyage_number     VARCHAR(20),
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tracking_handling_event_tracking FOREIGN KEY (tracking_id) REFERENCES tracking_activity (id)
);
