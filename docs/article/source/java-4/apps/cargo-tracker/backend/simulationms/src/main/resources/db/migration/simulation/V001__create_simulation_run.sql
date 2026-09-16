-- 業務シミュレーションの実行と工程（US33・US34 / [ADR-0020]）。
--
-- **Event Sourcing を適用しない**（[ADR-0020] 決定 3）。実行の記録は業務の事実では
-- なく、監査もリプレイも要らない。現在状態だけを持つ（authms と同じ扱い）。
CREATE TABLE simulation_run (
    run_id        VARCHAR(36)  PRIMARY KEY,
    scenario_id   VARCHAR(40)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    -- 乱数の種（US36・IT17）。**列は本 IT で置く**——あとから足すと、
    -- IT16 の実行だけ種が無く再現できない。手で選んだ実行では NULL。
    seed          BIGINT,
    started_at    TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ,
    started_by    VARCHAR(64)  NOT NULL,
    projected_at  TIMESTAMPTZ  NOT NULL
);

-- **実行中の同じシナリオは 1 本だけ**（US33 §受入基準 5）。
-- PostgreSQL の UNIQUE は NULL を互いに違う値として扱うので、
-- 「実行中のものだけ」を部分インデックスで絞る。
CREATE UNIQUE INDEX ux_simulation_run_scenario_running
    ON simulation_run (scenario_id) WHERE status = 'RUNNING';

CREATE INDEX ix_simulation_run_started_at ON simulation_run (started_at DESC);

CREATE TABLE simulation_step (
    run_id           VARCHAR(36)  NOT NULL REFERENCES simulation_run (run_id),
    step_no          INTEGER      NOT NULL,
    kind             VARCHAR(40)  NOT NULL,
    outcome          VARCHAR(20)  NOT NULL,
    elapsed_ms       BIGINT,
    -- その工程が生成した識別子（予約番号・追跡番号・請求番号）。
    -- **ここから業務画面へ行ける**ことが US34 §受入基準 5 である。
    produced_id      VARCHAR(64),
    failure_status   INTEGER,
    failure_message  TEXT,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (run_id, step_no)
);

COMMENT ON TABLE simulation_run IS '業務シミュレーションの実行（UC23）';
COMMENT ON TABLE simulation_step IS '実行の工程ごとの結果（US34）';
