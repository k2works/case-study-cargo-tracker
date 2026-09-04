-- 連鎖の途中経過。
--
-- 正典: docs/design/cargo-tracker/data-model.md「連鎖の途中経過（process_state）」
--
-- Axon 5 に Saga が無いので、複数段にまたがる連鎖の途中経過をここに明示的に持つ
-- （ADR-0001 決定 6）。Saga のストアに直列化して埋めるのと違い、滞留の一覧化も
-- 管理画面もふつうの SQL で書ける。
--
-- 1 段で終わる連鎖には行を作らない。集約から「今どの段か」が読めるので、
-- 増やすと持ち主が二重になる。

CREATE TABLE process_state (
    process_type    VARCHAR(50)  NOT NULL,
    process_id      VARCHAR(36)  NOT NULL,
    current_step    VARCHAR(50)  NOT NULL,
    total_steps     INTEGER      NOT NULL,
    completed_steps INTEGER      NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    -- 連鎖の再開・補償に要る値。個人情報は入れない（鍵で消せなくなる）。
    metadata        JSONB,
    started_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (process_type, process_id),
    CONSTRAINT process_state_status_check
        CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPENSATED')),
    CONSTRAINT process_state_steps_check
        CHECK (completed_steps >= 0 AND completed_steps <= total_steps),
    -- 終わったものには終わった時刻がある。無いまま COMPLETED になっていると、
    -- 「いつ終わったか」を後から問えない。
    CONSTRAINT process_state_completed_at_check
        CHECK ((status = 'RUNNING') = (completed_at IS NULL))
);

-- 滞留の走査（status = 'RUNNING' かつ updated_at が古い行）。
CREATE INDEX idx_process_state_stuck
    ON process_state (process_type, updated_at)
    WHERE status = 'RUNNING';
