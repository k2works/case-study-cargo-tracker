-- 荷主の投影と要確認一覧。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 個人情報の列（name / email / phone / address）は NULL 許容にする。
-- 鍵を破棄したあとのリプレイで復号結果が null になり、NOT NULL だと投影が
-- 止まるため（ADR-0003）。email の UNIQUE は NULL を許す。

CREATE TABLE shipper (
    shipper_id      VARCHAR(36)  PRIMARY KEY,
    shipper_code    VARCHAR(10)  NOT NULL UNIQUE,
    shipper_type    VARCHAR(30)  NOT NULL,
    name            VARCHAR(200),
    email           VARCHAR(255) UNIQUE,
    phone           VARCHAR(30),
    address         VARCHAR(400),
    country_code    VARCHAR(2)   NOT NULL,
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(5,4),
    registered_at   TIMESTAMPTZ  NOT NULL,
    projected_at    TIMESTAMPTZ  NOT NULL,
    last_event_id   VARCHAR(36)
);

-- 荷主コードの採番。集約で MAX+1 しない（data-model.md）。
-- 自前採番はシーケンスと衝突し、原因でないテストが UNIQUE 制約で落ちる。
CREATE SEQUENCE shipper_code_seq START WITH 1 INCREMENT BY 1;

-- 投影が弾いたもの・連鎖が補償に至ったものを担当ロールの一覧（S70）に出す。
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
