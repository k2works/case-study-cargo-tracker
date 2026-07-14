-- shipper（荷主・US02/US03）。SQLite 方言。
CREATE TABLE shipper (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    shipper_code    TEXT    NOT NULL UNIQUE,
    shipper_type    TEXT    NOT NULL,
    name            TEXT    NOT NULL,
    email           TEXT    NOT NULL,
    phone           TEXT,
    contract_number TEXT,
    discount_rate   NUMERIC NOT NULL DEFAULT 0,
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0
);
