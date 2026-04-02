CREATE TABLE handling_events (
    id               UUID         NOT NULL,
    booking_id       UUID         NOT NULL,
    event_type       VARCHAR(20)  NOT NULL,
    location_code    VARCHAR(10)  NOT NULL,
    completion_time  TIMESTAMP    NOT NULL,
    memo             VARCHAR(500),
    registered_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_handling_events PRIMARY KEY (id),
    CONSTRAINT fk_handling_events_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);
