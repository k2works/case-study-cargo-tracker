CREATE TABLE tracking_handling_event (
    id                  BIGSERIAL PRIMARY KEY,
    tracking_number     VARCHAR(30)   NOT NULL,
    event_type          VARCHAR(20)   NOT NULL,
    location_unlocode   VARCHAR(10)   NOT NULL,
    completion_time     TIMESTAMP     NOT NULL,
    voyage_number       VARCHAR(20),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_handling_tracking FOREIGN KEY (tracking_number)
        REFERENCES tracking_record(tracking_number)
);
