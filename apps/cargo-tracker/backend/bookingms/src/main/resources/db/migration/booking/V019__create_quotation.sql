-- 輸送見積（US01・IT14）。正典の ER にはあったが実体が無かった（注 N4）。
--
-- **Cargo には混ぜない。** 見積は予約の前段にあり、予約に至らない見積が存在する。
-- 予約の表に見積の列を足すと、「予約されていない予約」という読みにくい行ができる。
CREATE TABLE quotation (
    quotation_id         VARCHAR(36)   PRIMARY KEY,
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    arrival_deadline     DATE          NOT NULL,
    cargo_type           VARCHAR(30)   NOT NULL,
    weight_kg            NUMERIC(12,2) NOT NULL,
    -- いちばん安い候補の概算。**候補が無ければ 0 円**——「見積できなかった」では
    -- なく「期限に間に合う経路が無い」が答えなので、見積そのものは成り立つ。
    estimated_amount     NUMERIC(14,2) NOT NULL,
    estimated_currency   VARCHAR(3)    NOT NULL,
    valid_until          DATE          NOT NULL,
    created_by           VARCHAR(50)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    projected_at         TIMESTAMPTZ   NOT NULL
);

-- 一覧は新しい順（S12 から作ったものをすぐ開く）。
CREATE INDEX idx_quotation_created ON quotation (created_at DESC);

-- ルート候補（US01 §受入基準 3）。
--
-- **経路そのものは持たない。** 正典の ER は航海番号の並びだけを持つ
-- （voyage_numbers VARCHAR(200)）。経路は予約のとき cargo_leg に写す——
-- 見積の候補を旅程として持つと、選ばれなかった候補まで旅程の形で残る。
CREATE TABLE quotation_candidate (
    quotation_id       VARCHAR(36)   NOT NULL,
    candidate_seq      INTEGER       NOT NULL,
    voyage_numbers     VARCHAR(200)  NOT NULL,
    transit_days       INTEGER       NOT NULL,
    estimated_cost     NUMERIC(14,2) NOT NULL,
    estimated_currency VARCHAR(3)    NOT NULL,
    -- 希望期限からの超過日数。**候補が答える**（画面に数え直させない）。
    -- 0 なら間に合う。
    overdue_days       INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (quotation_id, candidate_seq),
    FOREIGN KEY (quotation_id) REFERENCES quotation (quotation_id) ON DELETE CASCADE
);
