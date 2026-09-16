-- 継続実行の稼働（US36 / [ADR-0020]）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「simulation_read_db」
--
-- **実行 1 本（simulation_run）とは寿命が違う。** 稼働は何時間も生き、実行は
-- 数十秒で終わる。1 つの表に混ぜると、実行を 1 本記録するたびに稼働ごと書き直す
-- ことになる。
--
-- **投影ではなく、ここが正である**（ADR-0020 決定 3）。
CREATE TABLE simulation_schedule (
    schedule_id      VARCHAR(36)  PRIMARY KEY,
    seed             BIGINT       NOT NULL,
    interval_seconds INTEGER      NOT NULL,
    max_concurrent   INTEGER      NOT NULL,
    exception_ratio  NUMERIC(3,2) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    started_by       VARCHAR(64)  NOT NULL,
    started_at       TIMESTAMPTZ  NOT NULL,
    stopped_at       TIMESTAMPTZ,
    projected_at     TIMESTAMPTZ  NOT NULL
);

-- **稼働は 1 本**（US36 §4 の前提）。2 本走ると上限が 2 倍になり、
-- 「設定した上限を超えて実行しない」（§2）が守れない。
--
-- **数えてから入れる形にしない**（実行の二重起動と同じ理由）。2 つの要求が
-- 同時に来たときに両方とも通る。
CREATE UNIQUE INDEX uq_simulation_schedule_active
    ON simulation_schedule ((TRUE)) WHERE status <> 'STOPPED';

-- 一覧は新しい順。
CREATE INDEX idx_simulation_schedule_started_at ON simulation_schedule (started_at DESC);

-- どの稼働が流した実行か（US36 §8 の統計が数える）。
-- **手で流した実行では NULL。** 既定が業務上正しいので、既存行はそのままでよい。
ALTER TABLE simulation_run
    ADD COLUMN schedule_id VARCHAR(36);

COMMENT ON COLUMN simulation_run.schedule_id IS
    'どの稼働が流したか（US36）。手で流した実行では NULL';

CREATE INDEX idx_simulation_run_schedule ON simulation_run (schedule_id);
