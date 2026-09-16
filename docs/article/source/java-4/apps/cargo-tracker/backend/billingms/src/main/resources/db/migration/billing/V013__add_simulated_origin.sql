-- シミュレーション由来の印（US33 §受入基準 3 / [ADR-0020]）。
--
-- **印だけでは混ざらない。** 除外は読み口の側に置く——請求一覧が既定で外す。
-- 経理の締めに、シミュレーションが作った請求書が並ぶのを防ぐ。
--
-- **既定は FALSE。** 列が無かったころの荷主と請求書はすべて本物である。
ALTER TABLE shipper_contract_snapshot
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE invoice
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_invoice_simulated ON invoice (simulated);

COMMENT ON COLUMN shipper_contract_snapshot.simulated IS
    'シミュレーションが作った荷主か（US33）';
COMMENT ON COLUMN invoice.simulated IS
    'シミュレーションが作った請求書か（US33）。荷主から引き継ぐ';
