-- Tracking Context: 追跡レコード（集約ルート）と追跡イベント・例外イベント（US14/US18・IT6）。
CREATE TABLE tracking_activity (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20)  NOT NULL UNIQUE,
    booking_id       VARCHAR(20)  NOT NULL,
    transport_status VARCHAR(30)  NOT NULL DEFAULT 'NOT_RECEIVED',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_activity_booking_id ON tracking_activity (booking_id);

CREATE TABLE tracking_handling_event (
    id                BIGSERIAL PRIMARY KEY,
    tracking_id       BIGINT       NOT NULL REFERENCES tracking_activity (id) ON DELETE CASCADE,
    event_type        VARCHAR(30)  NOT NULL,
    transport_status  VARCHAR(30)  NOT NULL,
    event_time        TIMESTAMP    NOT NULL,
    location_unlocode VARCHAR(5)   NOT NULL,
    voyage_number     VARCHAR(20),
    seq_number        INT          NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_handling_event_tracking_id ON tracking_handling_event (tracking_id);

CREATE TABLE tracking_exception_event (
    id             BIGSERIAL PRIMARY KEY,
    tracking_id    BIGINT       NOT NULL REFERENCES tracking_activity (id) ON DELETE CASCADE,
    exception_type VARCHAR(50)  NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    escalation_flag BOOLEAN     NOT NULL DEFAULT FALSE,
    description    VARCHAR(500),
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_exception_event_tracking_id ON tracking_exception_event (tracking_id);
