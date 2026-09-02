-- 継続実行（US37・[ADR-031]）。
--
-- 種を残すのは、**落ちた実行を再現するため**である（決定 1）。種を残さない
-- ランダム実行は、落ちても報告手段にならない——再実行して通ったことを理由に
-- 見送ることになる。
CREATE TABLE IF NOT EXISTS simulation_session (
    id                BIGSERIAL      NOT NULL,
    session_id        VARCHAR(40)    NOT NULL,
    seed              BIGINT         NOT NULL,
    interval_seconds  INTEGER        NOT NULL,
    max_concurrent    INTEGER        NOT NULL,
    exception_ratio   NUMERIC(3,2)   NOT NULL,
    -- 実行中・停止処理中・停止済み。**「止めた」と「止まった」を分ける**（決定 4）
    -- ——分けないと、進行中が残っているのに停止済みと表示され、統計が確定して
    -- いない状態で読まれる
    status            VARCHAR(20)    NOT NULL,
    started_by        VARCHAR(50)    NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE   NOT NULL,
    stopped_at        TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_simulation_session PRIMARY KEY (id),
    CONSTRAINT uk_simulation_session_session_id UNIQUE (session_id)
);

-- **既存行は種を持たない。** 不変条件を後から足すと、列が無かったころの行が
-- 読めなくなる。既定値で埋めてから NOT NULL にする。
-- **IF NOT EXISTS は書かない。** 設計との突き合わせが IF を列名として読む。
-- Flyway は同じ版を二度当てないので、要らない。
ALTER TABLE simulation_run ADD COLUMN seed BIGINT;

UPDATE simulation_run SET seed = 0 WHERE seed IS NULL;

ALTER TABLE simulation_run ALTER COLUMN seed SET NOT NULL;

-- 継続実行が生んだ実行は、どのセッションのものかを持つ。
-- **NULL 可**——管理者が手で押した実行はセッションを持たない。
ALTER TABLE simulation_run ADD COLUMN session_id BIGINT;

ALTER TABLE simulation_run
    ADD CONSTRAINT fk_simulation_run_session FOREIGN KEY (session_id)
        REFERENCES simulation_session (id);

CREATE INDEX IF NOT EXISTS idx_simulation_run_session
    ON simulation_run (session_id);

CREATE INDEX IF NOT EXISTS idx_simulation_session_status
    ON simulation_session (status);
