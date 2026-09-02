-- 更新されない列を落とす（IT15 Phase 0.5）。
--
-- status と finished_at は挿入時に一度書かれるだけで、その後は更新されなかった。
-- 読み出しでは集約が工程の結果から導き直しているため、列の値は誰も読んでいない。
-- 残しておくと、DB を直接見た人は**すべての実行が RUNNING のまま**という嘘を読む。
-- 状態を二重に持たないという [ADR-030] 決定 5 の帰結として、列ごと落とす。
--
-- 索引も status を張っていたが、実行中の判定は工程の結果から導くため使われていない。
-- シナリオ ID だけに張り替える。

DROP INDEX IF EXISTS idx_simulation_run_scenario_status;

CREATE INDEX IF NOT EXISTS idx_simulation_run_scenario
    ON simulation_run (scenario_id);

ALTER TABLE simulation_run DROP COLUMN IF EXISTS status;

ALTER TABLE simulation_run DROP COLUMN IF EXISTS finished_at;
