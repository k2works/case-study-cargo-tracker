-- billingms Read Model（IT7 US21・US22・US23）
-- data-model.md L684-760 billing_read_db の ER 図と整合（Invoice 単一集約 + 内訳 + 入金履歴）。
-- 監査カラム（created_at / updated_at / version）は他サービスと同様に明示する。
-- H2 互換性のため TIMESTAMP（TIMESTAMPTZ ではなく）を使う（既存サービス慣習）。

-- invoice: Invoice 集約の Read Model。
-- 1 予約 1 請求書（UNIQUE(booking_id)）、発行後の invoice_number 一意性（NULL 多重許容）、
-- 金額の整合性は CHECK 制約で担保（domain-model.md L960-966、ADR 集約の不変条件と同等）。
CREATE TABLE IF NOT EXISTS invoice (
    invoice_id           VARCHAR(36)   NOT NULL,
    booking_id           VARCHAR(36)   NOT NULL,
    shipper_id           VARCHAR(36)   NOT NULL,
    basic_amount         NUMERIC(14,2) NOT NULL,
    discount_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    adjustment_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount         NUMERIC(14,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    billing_status       VARCHAR(16)   NOT NULL,  -- PENDING / CALCULATED / INVOICED / PAID / OVERDUE / CANCELLED
    invoice_number       VARCHAR(30),             -- 発行（INVOICED）時に採番、それ以前は NULL
    payment_due          DATE,                    -- 発行時に確定（PaymentDuePolicy = issued + 30 日）
    paid_at              TIMESTAMP,               -- PAID 遷移時に確定
    cancellation_reason  TEXT,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (invoice_id),
    CONSTRAINT uq_invoice_booking UNIQUE (booking_id),
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),
    CONSTRAINT chk_invoice_total CHECK (total_amount = basic_amount - discount_amount + adjustment_amount),
    CONSTRAINT chk_invoice_amounts_non_negative CHECK (
        basic_amount >= 0 AND discount_amount >= 0 AND adjustment_amount >= 0
    ),
    CONSTRAINT chk_invoice_status CHECK (
        billing_status IN ('PENDING', 'CALCULATED', 'INVOICED', 'PAID', 'OVERDUE', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_invoice_shipper_status ON invoice (shipper_id, billing_status);
CREATE INDEX IF NOT EXISTS idx_invoice_status_due ON invoice (billing_status, payment_due);

-- invoice_line: 料金内訳明細（line_type 駆動設計、ADR-0015 派生）。
-- BASIC（基本料金）/ DISCOUNT（割引、負値）/ ADJUSTMENT（例外補償）/ SURCHARGE（割増、IT8 拡張）
-- 経理担当者が請求書 PDF を生成する際の入力（data-model.md 明細レベル）。
CREATE TABLE IF NOT EXISTS invoice_line (
    invoice_id   VARCHAR(36)   NOT NULL,
    line_seq     INTEGER       NOT NULL,
    line_type    VARCHAR(20)   NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    amount       NUMERIC(14,2) NOT NULL,
    reason_code  VARCHAR(40),
    PRIMARY KEY (invoice_id, line_seq),
    CONSTRAINT fk_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id),
    CONSTRAINT chk_invoice_line_type CHECK (
        line_type IN ('BASIC', 'DISCOUNT', 'ADJUSTMENT', 'SURCHARGE')
    )
);

-- payment: 入金履歴（IT7 では完全一致のみ受理、IT8 で部分入金対応予定）。
-- ADR-0015 PaymentMethod enum（BANK_TRANSFER / CREDIT_CARD / MANUAL）。
-- external_reference は決済機関の取引番号（IT8 で webhook 受信時に設定）。
CREATE TABLE IF NOT EXISTS payment (
    payment_id          VARCHAR(36)   NOT NULL,
    invoice_id          VARCHAR(36)   NOT NULL,
    paid_amount         NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    paid_at             TIMESTAMP     NOT NULL,
    payment_method      VARCHAR(40),
    external_reference  VARCHAR(100),
    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id),
    CONSTRAINT chk_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('BANK_TRANSFER', 'CREDIT_CARD', 'MANUAL')
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_invoice ON payment (invoice_id);
