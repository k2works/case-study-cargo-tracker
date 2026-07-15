-- handling_activity（荷役作業記録・US15/US16）。PostgreSQL 方言。
-- 引取（CLAIM）時の荷受人確認は consignee_confirmation に保持する。
CREATE TABLE handling_activity (
    id                     BIGSERIAL    PRIMARY KEY,
    booking_id             VARCHAR(20)  NOT NULL,
    event_type             VARCHAR(30)  NOT NULL,
    event_completion_time  TIMESTAMP    NOT NULL,
    location_unlocode      VARCHAR(5)   NOT NULL,
    voyage_number          VARCHAR(20),
    consignee_confirmation VARCHAR(255),
    operator_name          VARCHAR(200),
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    version                BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_handling_activity_booking_id ON handling_activity(booking_id);
