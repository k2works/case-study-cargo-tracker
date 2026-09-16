-- 輸送中の例外（US19 / UC16）と、例外の件数の非正規化。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_exception」
--
-- **追記系なので主キーは例外の識別子にする**（data-model.md:43）。採番すると、
-- 投影を読み直すたびに同じ内容の行が積み上がる。
--
-- **urgent は `ExceptionType#urgent` の結果を写す**（不変条件 7）。判定を投影に
-- 書き直さない。書き直すと、種別が増えたときに片方だけ直る。
--
-- **status_before_exception を summary に持つのは、画面が「解決すると何に戻るか」
-- を出せるようにするため。** 戻る先そのものは集約が覚えている（不変条件 5）。
--
-- **件数を非正規化するのは、一覧が tracking_exception を数えないため**
-- （data-model.md:571）。数えると、追跡一覧の 1 行ごとに往復が増える。

CREATE TABLE tracking_exception (
    exception_id     VARCHAR(36)  PRIMARY KEY,
    tracking_number  VARCHAR(25)  NOT NULL,
    exception_type   VARCHAR(30)  NOT NULL,
    response_status  VARCHAR(30)  NOT NULL,
    urgent           BOOLEAN      NOT NULL,
    unlocode         VARCHAR(5),
    description      TEXT         NOT NULL,
    resolution       TEXT,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    resolved_at      TIMESTAMPTZ,
    projected_at     TIMESTAMPTZ  NOT NULL
);

-- 例外一覧（S42）は「未解決を、緊急から、起きた順」で読む。
-- **残日数の並びはここでは作れない**——到着期限は tracking_summary が持つので、
-- 読み口が JOIN して並べる（data-model.md の注 N4）。
CREATE INDEX idx_tracking_exception_open
    ON tracking_exception (response_status, urgent DESC, occurred_at);

-- 追跡ごとの履歴（S41 の例外欄）。
CREATE INDEX idx_tracking_exception_tracking
    ON tracking_exception (tracking_number, occurred_at);

-- 一覧が tracking_exception を数えないための写し（data-model.md:571）。
ALTER TABLE tracking_summary
    ADD COLUMN open_exception_count   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN urgent_exception_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN status_before_exception VARCHAR(30);

-- 追跡一覧で「手を入れる場所」を先頭に出す（data-model.md:571）。
CREATE INDEX idx_tracking_summary_attention
    ON tracking_summary (urgent_exception_count DESC, last_status_changed_at);
