CREATE TABLE IF NOT EXISTS tracking_exception_event (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tracking_id           BIGINT        NOT NULL,
    exception_type        VARCHAR(30)   NOT NULL,
    occurred_at           TIMESTAMP     NOT NULL,
    location_unlocode     VARCHAR(5),
    reason                VARCHAR(500),
    escalation_flag       BOOLEAN       NOT NULL DEFAULT FALSE,
    response_content      VARCHAR(1000),
    new_estimated_arrival DATE,
    status                VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exception_tracking FOREIGN KEY (tracking_id) REFERENCES tracking_activity(id)
);
