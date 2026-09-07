-- 荷役の記録（US15 §受入基準 4 / IT9）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「handling_activity」
--
-- **追記系なので主キーは活動 ID にする**（data-model.md:43）。採番すると、
-- 投影を読み直すたびに同じ内容の行が積み上がる（IT2 で実在した欠陥）。
-- 活動 ID はクライアントが作るので、通信断の再送でも同じ鍵になる。
--
-- **consignee_name の書き手は引取（US16・IT10）。** 列は正典が定義しているので
-- 作るが、本 IT では常に NULL。

CREATE TABLE handling_activity (
    activity_id      VARCHAR(36)  PRIMARY KEY,
    tracking_number  VARCHAR(25)  NOT NULL,
    booking_id       VARCHAR(36)  NOT NULL,
    handling_type    VARCHAR(30)  NOT NULL,
    unlocode         VARCHAR(5)   NOT NULL,
    voyage_number    VARCHAR(20),
    consignee_name   VARCHAR(200),
    off_route        BOOLEAN      NOT NULL,
    operator         VARCHAR(50)  NOT NULL,
    completed_at     TIMESTAMPTZ  NOT NULL,
    voided           BOOLEAN      NOT NULL DEFAULT FALSE,
    voided_at        TIMESTAMPTZ,
    void_reason      TEXT,
    projected_at     TIMESTAMPTZ  NOT NULL
);

-- 荷役履歴（S51）は「その貨物の、起きた順」でしか読まない。
CREATE INDEX idx_handling_activity_history ON handling_activity (tracking_number, completed_at);

-- S50 の「この航海で送信済み」。荷役作業員は 1 隻から 20〜50 本を連続で記録する。
CREATE INDEX idx_handling_activity_voyage ON handling_activity (voyage_number, unlocode);
