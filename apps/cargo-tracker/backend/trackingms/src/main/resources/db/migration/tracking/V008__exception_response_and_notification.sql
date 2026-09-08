-- 例外の対応内容と、荷主へ知らせた記録を投影に残す（IT10 レビュー 高 2 件）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_exception」
--
-- **記録と読み口は対で出す。** IT10 の実装では
--   - 対応開始の「新しい到着予定日」「対応方針」を画面で入力させ、コマンドと
--     イベントまで運びながら、投影が `response_status` しか書いていなかった
--   - 荷主へ知らせた事実（`ExceptionShipperNotifiedEvent`）は Event Store に
--     積まれるだけで、購読する投影が 1 つも無かった
-- どちらも「入力した値が最後の層で消える」形で、画面には出ない。
--
-- **新しい到着予定日は一覧の並びに効く**（不変条件 7 の残日数）。対応して期限が
-- 動いたのに古い期限で並び続けると、緊急でないものが上に来る。

ALTER TABLE tracking_exception
    ADD COLUMN new_estimated_arrival DATE,
    ADD COLUMN response_plan         TEXT;

-- 荷主へ知らせた記録（US19 §受入基準 3）。**送信基盤はスコープ外**で、通知は
-- 現行の手作業（電話・メール）で行う。ここに残るのは「いつ・どうやって・何を
-- 伝えたか」で、荷主から「聞いていない」と言われたときに突き合わせる材料になる。
--
-- **追記系なので主キーは元イベントの識別子にする**（data-model.md:43）。
-- 採番すると、投影を読み直すたびに同じ内容の行が積み上がる。
CREATE TABLE exception_notification (
    event_id        VARCHAR(36)  PRIMARY KEY,
    tracking_number VARCHAR(25)  NOT NULL,
    exception_id    VARCHAR(36)  NOT NULL,
    means           VARCHAR(50)  NOT NULL,
    summary         TEXT         NOT NULL,
    notified_by     VARCHAR(50),
    notified_at     TIMESTAMPTZ  NOT NULL,
    projected_at    TIMESTAMPTZ  NOT NULL
);

-- S41 は「その例外に、いつ何を伝えたか」を起きた順に読む。
CREATE INDEX idx_exception_notification_exception
    ON exception_notification (exception_id, notified_at);
