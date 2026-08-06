-- 共有: 原子採番カウンタ（ADR-0008 T3 返済）。
-- 追跡番号・請求番号の日次連番を INSERT ... ON CONFLICT DO UPDATE RETURNING で原子的に採番する。
CREATE TABLE sequence_counter (
    name  VARCHAR(30) NOT NULL,
    day   DATE        NOT NULL,
    value INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (name, day)
);
