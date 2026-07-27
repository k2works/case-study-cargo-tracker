-- Billing Context: 精算書（請求書）と入金記録（US21/US22/US23）。
-- 予約・荷主は BC 独立性のため業務識別子（booking_id / shipper_code）で参照する。
-- 金額は最小通貨単位の INTEGER で保持（IT8 注1・data-model と一貫）。
CREATE TABLE invoice (
    id                    BIGSERIAL PRIMARY KEY,
    invoice_number        VARCHAR(30)  NOT NULL UNIQUE,
    booking_id            VARCHAR(20)  NOT NULL UNIQUE,
    shipper_code          VARCHAR(20)  NOT NULL,
    shipper_type          VARCHAR(20)  NOT NULL,
    base_amount_value     INTEGER      NOT NULL,
    discount_rate         NUMERIC(5, 4) NOT NULL DEFAULT 0.0000,
    discount_amount_value INTEGER      NOT NULL DEFAULT 0,
    tax_amount_value      INTEGER      NOT NULL DEFAULT 0,
    total_amount_value    INTEGER      NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    payment_status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    issued_at             TIMESTAMPTZ  NOT NULL,
    due_date              DATE         NOT NULL,
    paid_at               TIMESTAMPTZ,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE payment (
    id                    BIGSERIAL PRIMARY KEY,
    invoice_id            BIGINT       NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    paid_amount_value     INTEGER      NOT NULL,
    paid_at               TIMESTAMP    NOT NULL,
    payment_method        VARCHAR(30),
    transaction_reference VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);
