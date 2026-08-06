-- migrate:up

-- IT1 US04 貨物予約集約
-- 出発港 / 到着港は location テーブルへの FK 参照
CREATE TABLE cargo (
    id                          BIGSERIAL PRIMARY KEY,
    booking_id                  VARCHAR(20) NOT NULL UNIQUE,
    shipper_id                  BIGINT NOT NULL REFERENCES shipper(id),
    origin_unlocode             VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    destination_unlocode        VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    deadline                    TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_status              VARCHAR(20) NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 1,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT cargo_booking_id_format
        CHECK (booking_id ~ '^BK-[A-Z0-9]{6}$'),
    CONSTRAINT cargo_booking_status_check
        CHECK (booking_status IN ('Draft', 'Submitted', 'RouteProposed', 'Confirmed', 'Closed'))
);

CREATE INDEX cargo_shipper_id_idx ON cargo (shipper_id);
CREATE INDEX cargo_booking_status_idx ON cargo (booking_status);

-- migrate:down

DROP TABLE cargo;
