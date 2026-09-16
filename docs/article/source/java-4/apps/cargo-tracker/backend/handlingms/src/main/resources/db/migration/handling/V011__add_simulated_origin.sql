-- シミュレーション由来の印を handlingms へ運ぶ（US33 §受入基準 3 / [ADR-0020] 決定 4）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（handling_read_db）
--
-- **印は荷主に付く。** 契約イベント ShipperRegisteredEvent が運ぶので写しを持ち、
-- 貨物の写しを作るとき（TrackingInitializedEvent）に解決する。cargo_snapshot は
-- 荷主を持たないが、TrackingInitializedEvent は shipperId を運ぶ。
--
-- **既定は FALSE。** 列が無かったころの貨物はすべて本物である。
CREATE TABLE shipper_origin (
    shipper_id   VARCHAR(36)  PRIMARY KEY,
    simulated    BOOLEAN      NOT NULL DEFAULT FALSE,
    projected_at TIMESTAMPTZ  NOT NULL
);

COMMENT ON TABLE shipper_origin IS
    'シミュレーション由来かどうかだけを写す（ADR-0020 決定 4）。荷役に要るのは印だけである';

ALTER TABLE cargo_snapshot
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN cargo_snapshot.simulated IS
    'シミュレーション由来か。荷役の作業一覧（S50）・ダッシュボード（S02）・引取待ち（S54）は外す。単票は外さない';

CREATE INDEX ix_cargo_snapshot_simulated ON cargo_snapshot (simulated);
