-- 請求書テーブル
CREATE TABLE invoice (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number        VARCHAR(50)    NOT NULL UNIQUE,
    booking_id            VARCHAR(50)    NOT NULL,
    shipper_id            VARCHAR(50),
    base_amount_value     BIGINT         NOT NULL DEFAULT 0,
    base_amount_currency  VARCHAR(3)     NOT NULL DEFAULT 'JPY',
    final_amount_value    BIGINT         NOT NULL DEFAULT 0,
    final_amount_currency VARCHAR(3)     NOT NULL DEFAULT 'JPY',
    tax_rate              DECIMAL(5, 4)  NOT NULL DEFAULT 0.1000,
    tax_amount_value      BIGINT         NOT NULL DEFAULT 0,
    payment_status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    issued_at             DATE           NOT NULL,
    due_date              DATE           NOT NULL,
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 請求明細テーブル
CREATE TABLE invoice_line_item (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id      BIGINT         NOT NULL REFERENCES invoice(id),
    description     VARCHAR(200)   NOT NULL,
    amount_value    BIGINT         NOT NULL,
    amount_currency VARCHAR(3)     NOT NULL DEFAULT 'JPY',
    seq_number      INTEGER        NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
