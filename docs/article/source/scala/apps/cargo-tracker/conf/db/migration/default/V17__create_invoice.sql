-- IT6 US21 タスク 3.2: Billing Context のテーブル群
-- data-model.md L914-927 準拠 + IT8 US23 で payment テーブルを活用

CREATE SEQUENCE invoice_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE invoice (
    id                      BIGSERIAL PRIMARY KEY,
    invoice_number          VARCHAR(30) NOT NULL,
    booking_id              VARCHAR(20) NOT NULL,
    shipper_id              VARCHAR(20) NOT NULL,
    is_corporate            BOOLEAN NOT NULL DEFAULT FALSE,
    base_amount             BIGINT NOT NULL,
    discount_rate           NUMERIC(5,4) NOT NULL DEFAULT 0.0000,
    discount_policy_type    VARCHAR(30) NOT NULL DEFAULT 'None',
    final_amount            BIGINT NOT NULL,
    payment_status          VARCHAR(30) NOT NULL,
    issued_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at                 TIMESTAMP WITH TIME ZONE,
    version                 INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoice_booking UNIQUE (booking_id),
    CONSTRAINT ck_invoice_payment_status CHECK (
        payment_status IN ('Pending', 'Confirmed', 'Overdue', 'Refunded')
    ),
    CONSTRAINT ck_invoice_discount_rate CHECK (
        discount_rate >= 0.0000 AND discount_rate <= 0.3000
    ),
    CONSTRAINT ck_invoice_amounts CHECK (
        base_amount >= 0 AND final_amount >= 0
    )
);

CREATE INDEX idx_invoice_booking ON invoice (booking_id);
CREATE INDEX idx_invoice_payment_status ON invoice (payment_status);

-- 請求明細（基本料金内訳: 距離料金 / 重量料金 / 貨物種別料金 / 荷役回数加算など）
CREATE TABLE invoice_line_item (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    line_no         INTEGER NOT NULL,
    description     VARCHAR(200) NOT NULL,
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_invoice_line UNIQUE (invoice_id, line_no)
);

-- 支払い記録（IT8 US23 で活用）
CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES invoice(id),
    amount          BIGINT NOT NULL,
    payment_method  VARCHAR(30) NOT NULL,
    paid_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    reference_code  VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);
CREATE INDEX idx_payment_invoice ON payment (invoice_id);

-- 予約からの請求書参照
ALTER TABLE cargo ADD COLUMN invoice_id BIGINT REFERENCES invoice(id);
CREATE INDEX idx_cargo_invoice ON cargo (invoice_id);
