-- 追跡の一覧・詳細（US14 / IT7）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_read_db ER 図」
--
-- **本 IT で書く列だけを作る。** 荷役・例外・キャンセルの列
-- （current_unlocode / misrouted / open_exception_count / closed など）は、
-- それを書くイベントを実装する IT で足す。中身の無い列を先に作ると、
-- 画面が読んで「常に 0 件」を出し、動いていると誤解される。
--
-- shipper_id も作らない。TrackingInitializedEvent に荷主 ID が無く、trackingms は
-- それを得る手段を持たない（正典は NOT NULL だが、書く相手がいない）。
-- 荷主向け追跡（US18 / IT8）でイベントに足すときに、この列も足す。

CREATE TABLE tracking_summary (
    tracking_number        VARCHAR(25)  PRIMARY KEY,
    booking_id             VARCHAR(36)  NOT NULL UNIQUE,
    origin_unlocode        VARCHAR(5)   NOT NULL,
    destination_unlocode   VARCHAR(5)   NOT NULL,
    cargo_type             VARCHAR(20)  NOT NULL,
    transport_status       VARCHAR(30)  NOT NULL,
    initialized_at         TIMESTAMPTZ  NOT NULL,
    last_status_changed_at TIMESTAMPTZ  NOT NULL,
    projected_at           TIMESTAMPTZ  NOT NULL,
    last_event_id          VARCHAR(36)
);

-- 予約から追跡を引く（連鎖が通ったかの確認と、予約詳細からの導線）。
CREATE INDEX idx_tracking_summary_booking ON tracking_summary (booking_id);

-- 予定の旅程。荷役（IT9）が予定と実績を照合する材料。
-- **順序が業務の意味を持つ**ので leg_seq を持ち、積む順に並べる。
CREATE TABLE tracking_leg (
    tracking_number  VARCHAR(25)  NOT NULL,
    leg_seq          INTEGER      NOT NULL,
    voyage_number    VARCHAR(30)  NOT NULL,
    load_unlocode    VARCHAR(5)   NOT NULL,
    unload_unlocode  VARCHAR(5)   NOT NULL,
    load_time        TIMESTAMPTZ  NOT NULL,
    unload_time      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (tracking_number, leg_seq),
    FOREIGN KEY (tracking_number) REFERENCES tracking_summary (tracking_number)
);
