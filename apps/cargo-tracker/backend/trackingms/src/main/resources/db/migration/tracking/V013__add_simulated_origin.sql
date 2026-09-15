-- シミュレーション由来の印を trackingms へ運ぶ（US33 §受入基準 3 / [ADR-0020] 決定 4）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（tracking_read_db）
--
-- **印は荷主に付く。** 契約イベント ShipperRegisteredEvent が運ぶので、billingms の
-- shipper_contract_snapshot と同じ形で写しを持ち、追跡を作るときに解決する。
-- 追跡は数万行になるが荷主は数百行なので、毎回の結合ではなく投影時に写す
-- （請求（V013__add_simulated_origin.sql / billing）と同じ判断）。
--
-- **既定は FALSE。** 列が無かったころの追跡はすべて本物である。
CREATE TABLE shipper_origin (
    shipper_id   VARCHAR(36)  PRIMARY KEY,
    simulated    BOOLEAN      NOT NULL DEFAULT FALSE,
    projected_at TIMESTAMPTZ  NOT NULL
);

COMMENT ON TABLE shipper_origin IS
    'シミュレーション由来かどうかだけを写す（ADR-0020 決定 4）。氏名などは持たない——追跡に要るのは印だけである';

ALTER TABLE tracking_summary
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tracking_summary.simulated IS
    'シミュレーション由来か。追跡管理者の一覧（S40）は既定で外す。単票は外さない——US34 の実行結果が辿る先である';

-- 一覧は simulated = FALSE で絞るので、部分索引で引く。
CREATE INDEX ix_tracking_summary_simulated ON tracking_summary (simulated);
