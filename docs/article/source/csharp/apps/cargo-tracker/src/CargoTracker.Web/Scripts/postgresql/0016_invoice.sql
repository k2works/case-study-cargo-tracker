-- 精算テーブル（PostgreSQL 方言・US21/US22/US23・data-model Billing Context）
-- 精算書発行（US21）で invoice を作成し、割引根拠を invoice_line_item、入金を payment に記録する。
-- 金額は Money 値オブジェクト（最小通貨単位 value + 通貨 currency）で保持する。
CREATE TABLE invoice (
    id                      BIGSERIAL   PRIMARY KEY,
    invoice_number          VARCHAR(30) NOT NULL,
    booking_id              VARCHAR(20) NOT NULL,
    shipper_id              VARCHAR(30) NOT NULL,
    shipper_type            VARCHAR(20) NOT NULL,
    base_amount_value       BIGINT      NOT NULL,
    base_amount_currency    VARCHAR(3)  NOT NULL,
    discount_rate           NUMERIC(5,4) NOT NULL DEFAULT 0,
    final_amount_value      BIGINT      NOT NULL,
    final_amount_currency   VARCHAR(3)  NOT NULL,
    payment_status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    issued_at               TIMESTAMP   NOT NULL,
    due_date                DATE        NOT NULL,
    paid_at                 TIMESTAMP,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP   NOT NULL,
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoice_booking UNIQUE (booking_id)
);

CREATE TABLE invoice_line_item (
    id                  BIGSERIAL   PRIMARY KEY,
    invoice_id          BIGINT      NOT NULL REFERENCES invoice(id),
    description         VARCHAR(200) NOT NULL,
    amount_value        BIGINT      NOT NULL,
    amount_currency     VARCHAR(3)  NOT NULL,
    seq_number          INTEGER     NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    CONSTRAINT uk_invoice_line_seq UNIQUE (invoice_id, seq_number)
);

CREATE TABLE payment (
    id                      BIGSERIAL   PRIMARY KEY,
    invoice_id              BIGINT      NOT NULL REFERENCES invoice(id),
    paid_amount_value       BIGINT      NOT NULL,
    paid_amount_currency    VARCHAR(3)  NOT NULL,
    paid_at                 TIMESTAMP   NOT NULL,
    payment_method          VARCHAR(30) NOT NULL,
    transaction_reference   VARCHAR(100),
    created_at              TIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP   NOT NULL
);
