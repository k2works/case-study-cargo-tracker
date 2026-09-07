-- 追跡の履歴（US17 §受入基準 3 / US18）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_event」
--
-- **追記系なので主キーは元イベントの識別子にする。** 採番すると、投影を読み直す
-- たびに同じ内容の行が積み上がる（IT2 で実在した欠陥。attention_item と同じ形）。
-- Axon のイベント識別子をそのまま入れるので、再配送もリプレイも同じ行に落ちる。
--
-- **本 IT で書く種別だけを扱う。** event_type は MANUAL（US17）と HANDLING（IT9）。
-- MISROUTE / EXCEPTION / RESOLVED / VOIDED は、それを書くイベントを実装する IT で
-- 足す（列挙で縛らないのは、種別が増えるたびにマイグレーションを重ねないため）。

CREATE TABLE tracking_event (
    event_id         VARCHAR(36)  PRIMARY KEY,
    tracking_number  VARCHAR(25)  NOT NULL,
    event_type       VARCHAR(20)  NOT NULL,
    previous_status  VARCHAR(30),
    new_status       VARCHAR(30)  NOT NULL,
    location         VARCHAR(5),
    occurred_at      TIMESTAMPTZ  NOT NULL,
    recorded_by      VARCHAR(64),
    projected_at     TIMESTAMPTZ  NOT NULL
);

-- 履歴は「その追跡の、起きた順」でしか読まない。
CREATE INDEX idx_tracking_event_history ON tracking_event (tracking_number, occurred_at);
