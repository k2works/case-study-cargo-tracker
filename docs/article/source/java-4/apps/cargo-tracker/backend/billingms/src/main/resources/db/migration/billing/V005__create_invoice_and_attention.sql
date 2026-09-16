-- 請求書と明細、要確認の受け皿（US21 / IT13）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「billing_read_db」

CREATE TABLE invoice (
    invoice_id        VARCHAR(36)  PRIMARY KEY,
    booking_id        VARCHAR(36)  NOT NULL,
    -- 有効中は ''、取り消したときに invoice_id を入れる（US23・IT14）。
    -- **有効な請求書は予約ごとに 1 通**（不変条件 2）を UNIQUE で守るための列。
    void_marker       VARCHAR(36)  NOT NULL DEFAULT '',
    shipper_id        VARCHAR(36)  NOT NULL,
    -- crypto-shredding で鍵を破棄すると復号できず NULL が届く（ADR-0003）。
    -- NOT NULL にすると投影がそこで止まる。
    shipper_name      VARCHAR(200),
    shipper_type      VARCHAR(30)  NOT NULL,
    contract_number   VARCHAR(50),
    base_amount       NUMERIC(14,2) NOT NULL,
    discount_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    adjustment_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(14,2) NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    discount_rate     NUMERIC(5,4),
    billing_status    VARCHAR(30)  NOT NULL,
    calculated_at     TIMESTAMPTZ  NOT NULL,
    projected_at      TIMESTAMPTZ  NOT NULL,
    last_event_id     VARCHAR(36)
);

-- **不変条件 2 の三段目のひとつ。** 画面の確認だけでは同時の 2 件が通る
-- （読んでからコマンドを送るので、2 つの要求が両方とも「無い」を見る）。
CREATE UNIQUE INDEX uq_invoice_booking_active ON invoice (booking_id, void_marker);

-- 荷主向け請求書（US23・IT14）の索引を兼ねる。
CREATE INDEX idx_invoice_shipper ON invoice (shipper_id);

-- **INDEX(billing_status, due_on) は置かない。** 期限（due_on）が決まるのは
-- 発行のとき（不変条件 3・US23・IT14）で、いま列も索引も作ると誰も書かない
-- 列が残る（IT12 の held_business_days と同じ形）。IT14 でまとめて足す。

CREATE TABLE invoice_line_item (
    invoice_id         VARCHAR(36)  NOT NULL,
    line_seq           INTEGER      NOT NULL,
    item_type          VARCHAR(30)  NOT NULL,
    description        VARCHAR(200) NOT NULL,
    amount             NUMERIC(14,2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    -- 調整行の根拠になった例外 ID（trackingms への論理参照）。S61 から例外へ飛ぶ。
    basis_exception_id VARCHAR(64),
    PRIMARY KEY (invoice_id, line_seq),
    FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id) ON DELETE CASCADE
);

-- 要確認一覧（S70）の受け皿。定義は booking_read_db の attention_item と同一。
--
-- **投影ではなく追記専用の受け皿**であり、リプレイで TRUNCATE しない。
-- item_id は採番せず「何が・どの対象で・なぜ」から導く（共有カーネルの
-- AttentionItemId）。採番すると、読み直すたびに同じ内容の行が積み上がる。
CREATE TABLE attention_item (
    item_id         VARCHAR(36)  PRIMARY KEY,
    kind            VARCHAR(30)  NOT NULL,
    target_type     VARCHAR(30)  NOT NULL,
    target_id       VARCHAR(36)  NOT NULL,
    assigned_role   VARCHAR(30)  NOT NULL,
    reason          VARCHAR(200) NOT NULL,
    payload         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(50)
);

CREATE INDEX idx_attention_item_role_open
    ON attention_item (assigned_role, occurred_at)
    WHERE acknowledged_at IS NULL;
