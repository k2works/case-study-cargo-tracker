-- V001: invoice / invoice_line / payment テーブル作成
--
-- billingms の Read Model。BillingProjectionEventHandler が Invoice 集約の
-- イベントを購読して INSERT/UPDATE する（CQRS Query Side）。
--
-- 設計準拠: docs/development/iteration_plan-8.md データモデルセクション
--          docs/design/data-model.md billing_read_db

CREATE TABLE IF NOT EXISTS invoice (
    invoice_id           VARCHAR(36)    NOT NULL PRIMARY KEY,
    booking_id           VARCHAR(36)    NOT NULL,
    shipper_id           VARCHAR(36)    NOT NULL,
    basic_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    adjustment_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'JPY',
    billing_status       VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    invoice_number       VARCHAR(30),
    payment_due          DATE,
    paid_at              TIMESTAMP,
    cancellation_reason  TEXT,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_invoice_booking_id UNIQUE (booking_id),
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT chk_invoice_status
        CHECK (billing_status IN ('PENDING', 'CALCULATED', 'INVOICED', 'PAID', 'OVERDUE', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_invoice_billing_status
    ON invoice (billing_status);
CREATE INDEX IF NOT EXISTS idx_invoice_shipper_id
    ON invoice (shipper_id);
CREATE INDEX IF NOT EXISTS idx_invoice_payment_due
    ON invoice (payment_due);

CREATE TABLE IF NOT EXISTS invoice_line (
    invoice_id  VARCHAR(36)    NOT NULL,
    line_seq    INTEGER        NOT NULL,
    line_type   VARCHAR(20)    NOT NULL,
    description VARCHAR(255)   NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL,
    reason_code VARCHAR(40),
    PRIMARY KEY (invoice_id, line_seq),
    CONSTRAINT fk_invoice_line_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_invoice_line_type
        CHECK (line_type IN ('BASIC', 'DISCOUNT', 'ADJUSTMENT', 'SURCHARGE'))
);

CREATE TABLE IF NOT EXISTS payment (
    payment_id         VARCHAR(36)    NOT NULL PRIMARY KEY,
    invoice_id         VARCHAR(36)    NOT NULL,
    paid_amount        NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL DEFAULT 'JPY',
    paid_at            TIMESTAMP      NOT NULL,
    payment_method     VARCHAR(40)    NOT NULL,
    external_reference VARCHAR(100),
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_payment_method
        CHECK (payment_method IN ('BANK_TRANSFER', 'CREDIT_CARD', 'CHECK'))
);

CREATE INDEX IF NOT EXISTS idx_payment_invoice_id
    ON payment (invoice_id);
