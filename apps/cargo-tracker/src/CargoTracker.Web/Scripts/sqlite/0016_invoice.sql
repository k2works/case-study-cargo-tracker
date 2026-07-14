-- 精算テーブル（SQLite 方言・US21/US22/US23・data-model Billing Context）
-- 精算書発行（US21）で invoice を作成し、割引根拠を invoice_line_item、入金を payment に記録する。
-- 金額は Money 値オブジェクト（最小通貨単位 value + 通貨 currency）で保持する。
CREATE TABLE invoice (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number          TEXT    NOT NULL,
    booking_id              TEXT    NOT NULL,
    shipper_id              TEXT    NOT NULL,
    shipper_type            TEXT    NOT NULL,
    base_amount_value       INTEGER NOT NULL,
    base_amount_currency    TEXT    NOT NULL,
    discount_rate           TEXT    NOT NULL DEFAULT '0',
    final_amount_value      INTEGER NOT NULL,
    final_amount_currency   TEXT    NOT NULL,
    payment_status          TEXT    NOT NULL DEFAULT 'PENDING',
    issued_at               TEXT    NOT NULL,
    due_date                TEXT    NOT NULL,
    paid_at                 TEXT,
    version                 INTEGER NOT NULL DEFAULT 0,
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL,
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoice_booking UNIQUE (booking_id)
);

CREATE TABLE invoice_line_item (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id          INTEGER NOT NULL REFERENCES invoice(id),
    description         TEXT    NOT NULL,
    amount_value        INTEGER NOT NULL,
    amount_currency     TEXT    NOT NULL,
    seq_number          INTEGER NOT NULL,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    CONSTRAINT uk_invoice_line_seq UNIQUE (invoice_id, seq_number)
);

CREATE TABLE payment (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id              INTEGER NOT NULL REFERENCES invoice(id),
    paid_amount_value       INTEGER NOT NULL,
    paid_amount_currency    TEXT    NOT NULL,
    paid_at                 TEXT    NOT NULL,
    payment_method          TEXT    NOT NULL,
    transaction_reference   TEXT,
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL
);
