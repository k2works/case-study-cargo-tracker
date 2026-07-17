-- invoice / invoice_line_item / payment（精算・US21/US22/US23）。PostgreSQL 方言。
-- 金額は Money（最小通貨単位 int64 + 通貨コード）を *_value / *_currency の 2 カラムへ写像する。
CREATE TABLE invoice (
    id                       BIGSERIAL   PRIMARY KEY,
    invoice_number           VARCHAR(30) NOT NULL UNIQUE,
    booking_id               VARCHAR(20) NOT NULL UNIQUE,
    shipper_id               VARCHAR(64) NOT NULL,
    base_amount_value        BIGINT      NOT NULL,
    base_amount_currency     VARCHAR(3)  NOT NULL,
    discount_rate            NUMERIC(5,4) NOT NULL,
    final_amount_value       BIGINT      NOT NULL,
    final_amount_currency    VARCHAR(3)  NOT NULL,
    payment_status           VARCHAR(30) NOT NULL,
    issued_at                TIMESTAMP   NOT NULL,
    due_date                 DATE,
    paid_at                  TIMESTAMP,
    created_at               TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE invoice_line_item (
    id            BIGSERIAL   PRIMARY KEY,
    invoice_id    BIGINT      NOT NULL REFERENCES invoice(id),
    description   VARCHAR(200) NOT NULL,
    amount_value  BIGINT      NOT NULL,
    amount_currency VARCHAR(3) NOT NULL,
    seq_number    INTEGER     NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE payment (
    id                    BIGSERIAL   PRIMARY KEY,
    invoice_id            BIGINT      NOT NULL REFERENCES invoice(id),
    paid_amount_value     BIGINT      NOT NULL,
    paid_amount_currency  VARCHAR(3)  NOT NULL,
    paid_at               TIMESTAMP   NOT NULL,
    payment_method        VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100),
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_line_item_invoice_id ON invoice_line_item(invoice_id);
CREATE INDEX idx_payment_invoice_id ON payment(invoice_id);
