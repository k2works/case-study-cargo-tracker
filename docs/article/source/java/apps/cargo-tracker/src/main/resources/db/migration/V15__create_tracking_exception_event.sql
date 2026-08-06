CREATE TABLE tracking_exception_event (
    id                  BIGSERIAL PRIMARY KEY,
    tracking_number     VARCHAR(30)   NOT NULL,
    exception_type      VARCHAR(20)   NOT NULL,
    location_unlocode   VARCHAR(10)   NOT NULL,
    occurrence_time     TIMESTAMP     NOT NULL,
    reason              TEXT,
    response_note       TEXT,
    escalation_flag     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_exception_tracking FOREIGN KEY (tracking_number)
        REFERENCES tracking_record(tracking_number)
);
