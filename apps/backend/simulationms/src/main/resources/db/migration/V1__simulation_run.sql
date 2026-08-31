-- 業務シミュレーションの実行記録（US34・US35・[ADR-030] 決定 5）。
--
-- 失敗しても巻き戻さない。どこまで進んだかを追えることが US35 の目的であり、
-- 巻き戻すと失敗の痕跡が消える。工程ごとに 1 行を残す。
CREATE TABLE IF NOT EXISTS simulation_run (
    id            BIGSERIAL     NOT NULL,
    run_id        VARCHAR(40)   NOT NULL,
    scenario_id   VARCHAR(40)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    started_by    VARCHAR(50)   NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE   NOT NULL,
    finished_at   TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_simulation_run PRIMARY KEY (id),
    CONSTRAINT uk_simulation_run_run_id UNIQUE (run_id)
);

-- 同じシナリオを二重に走らせない（US34-5）。
-- 実行中は高々 1 件という制約は、部分 UNIQUE では H2 が解釈しないため
-- （IT12 で実測）、アプリケーション側の検査で守る。ここでは検索のための索引だけ置く。
CREATE INDEX IF NOT EXISTS idx_simulation_run_scenario_status
    ON simulation_run (scenario_id, status);

-- 工程ごとの結果。成否・所要時間・生成した識別子・失敗理由を残す（US35）。
CREATE TABLE IF NOT EXISTS simulation_step_result (
    id                  BIGSERIAL     NOT NULL,
    run_id              BIGINT        NOT NULL,
    step                VARCHAR(40)   NOT NULL,
    outcome             VARCHAR(20)   NOT NULL,
    elapsed_ms          INTEGER       NOT NULL,
    created_identifier  VARCHAR(40),
    failure_reason      TEXT,
    recorded_at         TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_simulation_step_result PRIMARY KEY (id),
    CONSTRAINT fk_simulation_step_result_run FOREIGN KEY (run_id)
        REFERENCES simulation_run (id)
);

CREATE INDEX IF NOT EXISTS idx_simulation_step_result_run
    ON simulation_step_result (run_id);
