-- migrate:up

-- IT8 US23 精算処理 - 精算書 (請求書) テーブル
--
-- iteration_plan-8.md §DB マイグレーション / data-model.md §invoice に対応。
-- Payment は独立テーブルとせず、paid_at / payment_reference を invoice に
-- 統合する (Scala 版 ADR 0019 と同方針、data-model.md 設計判断)。
-- BC 間 FK 制約なし: booking_id は cargo.booking_id と Application 層で照合
-- (data-model.md 設計判断 §5、DDD コンテキスト疎結合)。
--
-- Domain: Cargotracker.Billing.Domain.Model.Invoice
-- Port: Cargotracker.Billing.Application.Ports.InvoiceRepository
-- Commands: GenerateInvoice / IssuePayment / ConfirmPayment / OverdueCheck

CREATE TABLE invoice (
    id                     BIGSERIAL PRIMARY KEY,
    invoice_number         VARCHAR(30)  NOT NULL UNIQUE,
    booking_id             VARCHAR(20)  NOT NULL UNIQUE, -- 1 予約 1 請求
    shipper_id             VARCHAR(20)  NOT NULL,
    base_amount_value      BIGINT       NOT NULL,        -- 最小通貨単位
    base_amount_currency   VARCHAR(3)   NOT NULL,
    discount_rate          NUMERIC(5,4) NOT NULL DEFAULT 0,
    final_amount_value     BIGINT       NOT NULL,
    final_amount_currency  VARCHAR(3)   NOT NULL,
    tax_rate               NUMERIC(5,4) NOT NULL DEFAULT 0.1000,
    tax_amount             BIGINT       NOT NULL DEFAULT 0,
    payment_status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING','CONFIRMED','OVERDUE','REFUNDED')),
    issued_at              TIMESTAMPTZ,
    due_date               DATE,
    paid_at                TIMESTAMPTZ,
    payment_reference      VARCHAR(64),
    version                INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT inv_amounts_nonnegative
        CHECK (base_amount_value >= 0 AND final_amount_value >= 0 AND tax_amount >= 0)
);

CREATE INDEX idx_invoice_by_status ON invoice (payment_status, due_date);

-- migrate:down

DROP TABLE invoice;
