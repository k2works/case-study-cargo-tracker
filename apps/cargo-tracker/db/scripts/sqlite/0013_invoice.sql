-- invoice / invoice_line_item / payment（精算・US21/US22/US23）。SQLite 方言。
-- 金額は Money（最小通貨単位 int64 + 通貨コード）を *_value / *_currency の 2 カラムへ写像する。
CREATE TABLE invoice (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number        TEXT    NOT NULL UNIQUE,
    booking_id            TEXT    NOT NULL UNIQUE,
    shipper_id            TEXT    NOT NULL,
    base_amount_value     INTEGER NOT NULL,
    base_amount_currency  TEXT    NOT NULL,
    discount_rate         NUMERIC NOT NULL,
    final_amount_value    INTEGER NOT NULL,
    final_amount_currency TEXT    NOT NULL,
    payment_status        TEXT    NOT NULL,
    issued_at             TEXT    NOT NULL,
    due_date              TEXT,
    paid_at               TEXT,
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL
);

CREATE TABLE invoice_line_item (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id      INTEGER NOT NULL REFERENCES invoice(id),
    description     TEXT    NOT NULL,
    amount_value    INTEGER NOT NULL,
    amount_currency TEXT    NOT NULL,
    seq_number      INTEGER NOT NULL,
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL
);

CREATE TABLE payment (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id            INTEGER NOT NULL REFERENCES invoice(id),
    paid_amount_value     INTEGER NOT NULL,
    paid_amount_currency  TEXT    NOT NULL,
    paid_at               TEXT    NOT NULL,
    payment_method        TEXT    NOT NULL,
    transaction_reference TEXT,
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL
);

CREATE INDEX idx_invoice_line_item_invoice_id ON invoice_line_item(invoice_id);
CREATE INDEX idx_payment_invoice_id ON payment(invoice_id);
