-- 精算書（US21・US22・[ADR-027]）。
--
-- 金額は NUMERIC(15,2) にする（決定 2・注 3）。data-model.md の定義は INTEGER
-- （最小通貨単位）だが、**丸めの単位（円）と保存の単位を一致させる**ほうが、
-- 読んだときに何が起きたか分かる。丸めは Money が済ませている。
--
-- base_amount_* / discount_rate / shipper_id は本 IT で追加した列である（注 11）。
-- 正典は割引を額（discount_amount_*）だけで持つが、受入基準 22-4 は**率も精算書に
-- 載せる**ことを求めており、額から割り戻すと丸めの分だけずれる。

CREATE TABLE invoice (
    id                         BIGSERIAL PRIMARY KEY,
    invoice_number             VARCHAR(30)   NOT NULL UNIQUE,
    -- **二重請求を防ぐ**（正典のビジネスルール 5・決定 4）。集約でも守るが、
    -- 同時に 2 回押されたときに通してしまうのは制約だけが止められる
    booking_id                 VARCHAR(20)   NOT NULL UNIQUE,
    shipper_id                 VARCHAR(20)   NOT NULL,
    shipper_name               VARCHAR(200)  NOT NULL,
    shipper_corporate          BOOLEAN       NOT NULL,

    -- 基本料金の根拠（決定 1）。**距離は持っていないため区間数で代替する**
    leg_count                  INTEGER       NOT NULL,
    weight_kg                  NUMERIC(10,3) NOT NULL,
    cargo_type                 VARCHAR(20)   NOT NULL,

    base_amount_value          NUMERIC(15,2) NOT NULL,
    base_amount_currency       VARCHAR(3)    NOT NULL,
    -- 割引率。**未設定は 0% ではない**（[ADR-012]）ため NULL を許す
    discount_rate              NUMERIC(5,4),
    discount_amount_value      NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount_currency   VARCHAR(3)    NOT NULL DEFAULT 'JPY',

    -- キャンセル料の算定根拠（US30-9）。キャンセルでなければ NULL
    cancellation_fee_value     NUMERIC(15,2),
    cancellation_fee_currency  VARCHAR(3),
    cancellation_fee_rate      NUMERIC(5,4),
    booking_status_at_cancel   VARCHAR(30),

    tax_rate                   NUMERIC(5,4)  NOT NULL DEFAULT 0.1000,
    tax_amount                 NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount_value         NUMERIC(15,2) NOT NULL,
    total_amount_currency      VARCHAR(3)    NOT NULL,

    payment_status             VARCHAR(30)   NOT NULL,
    issued_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    due_date                   DATE,

    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_booking ON invoice (booking_id);
CREATE INDEX idx_invoice_payment_status ON invoice (payment_status);

-- 精算明細（決定 6）。料金調整を基本料金に混ぜず、根拠つきで積む
CREATE TABLE invoice_line_item (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT        NOT NULL REFERENCES invoice (id),
    description     VARCHAR(200)  NOT NULL,
    amount_value    NUMERIC(15,2) NOT NULL,
    amount_currency VARCHAR(3)    NOT NULL,
    seq_number      INTEGER       NOT NULL
);

CREATE INDEX idx_invoice_line_item_invoice ON invoice_line_item (invoice_id);

-- 請求番号の採番（[ADR-011] と同じ形）。**DB のシーケンスに任せる**
-- ——MAX+1 の自前採番は、同時に 2 件発行されたときに衝突する
CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;
