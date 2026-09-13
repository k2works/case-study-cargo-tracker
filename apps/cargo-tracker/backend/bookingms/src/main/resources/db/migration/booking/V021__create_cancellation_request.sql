-- キャンセル申請の投影（UC22 / US30。IT15 T3）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（ER の cancellation_request）
--
-- **承認待ちの一覧（S23）と、予約詳細の履歴（S22）が読む。** 集約が申請を
-- 持っているのは「二重に申請させない」ためで、**人が読む口はここ**である
-- （記録と読み口は対で出す）。
--
-- **投影に業務制約（CHECK）を置かない。** 制約は集約が守る——投影の CHECK が
-- 集約と食い違うと、リプレイが途中で止まる。
CREATE TABLE cancellation_request (
    request_id         VARCHAR(36)  PRIMARY KEY,
    booking_id         VARCHAR(36)  NOT NULL,
    reason             TEXT         NOT NULL,
    requested_by       VARCHAR(50)  NOT NULL,
    requested_at       TIMESTAMPTZ  NOT NULL,
    -- 判断。**NULL が承認待ち**（`APPROVED` / `REJECTED` / NULL）。
    -- 別の列（pending BOOLEAN）を置かない——同じ事実を 2 か所が持つと、
    -- 片方だけが更新された行が生まれる。
    decision           VARCHAR(30),
    discharge_unlocode VARCHAR(5),
    decision_reason    TEXT,
    decided_by         VARCHAR(50),
    decided_at         TIMESTAMPTZ,
    projected_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_cancellation_request_booking ON cancellation_request (booking_id);
-- 承認待ちの一覧（S23）が引く。**NULL = 承認待ち**。
CREATE INDEX idx_cancellation_request_decision ON cancellation_request (decision);
