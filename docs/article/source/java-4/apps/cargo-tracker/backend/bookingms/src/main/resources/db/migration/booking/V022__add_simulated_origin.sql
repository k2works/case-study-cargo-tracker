-- シミュレーション由来の印（US33 §受入基準 3 / [ADR-0020]）。
--
-- **印だけでは混ざらない。** 除外は読み口の側に置く——予約一覧・要確認一覧が
-- 既定で外す。印を付けるだけにすると、実データとして業務の一覧に並ぶ。
--
-- **既定は FALSE。** 列が無かったころの荷主と予約はすべて本物なので、
-- 既定値が業務上正しい。
ALTER TABLE shipper
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cargo_summary
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

-- 一覧は既定で除外するので、絞り込みに効く。
CREATE INDEX ix_cargo_summary_simulated ON cargo_summary (simulated);

COMMENT ON COLUMN shipper.simulated IS
    'シミュレーションが作った荷主か（US33）。業務の一覧は既定で除外する';
COMMENT ON COLUMN cargo_summary.simulated IS
    'シミュレーションが作った予約か（US33）。荷主から引き継ぐ';
