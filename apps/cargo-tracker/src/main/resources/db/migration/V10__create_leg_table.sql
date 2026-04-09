CREATE TABLE leg (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cargo_id                   BIGINT NOT NULL REFERENCES cargo(id),
    voyage_number              VARCHAR(20) NOT NULL,
    load_location_unlocode     VARCHAR(5)  NOT NULL,
    unload_location_unlocode   VARCHAR(5)  NOT NULL,
    load_time                  TIMESTAMP,
    unload_time                TIMESTAMP,
    seq_number                 INTEGER NOT NULL,
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW()
);
