-- 荷役が予定ルートを判定するための読み取りモデル（US15 / IT9）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「handling_read_db」
--
-- **ACL の読み取りモデル。** 契約イベント TrackingInitializedEvent を購読して
-- handlingms 側が作る（ADR-0012）。Booking / Tracking の型は持ち込まない。
--
-- **元イベントは ADR-0012 で決め直した。** 正典は TrackingNumberIssuedEvent と
-- 指定していたが、これは bookingms の内部イベントで購読できない。

CREATE TABLE cargo_snapshot (
    tracking_number      VARCHAR(25)  PRIMARY KEY,
    booking_id           VARCHAR(36)  NOT NULL,
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    cargo_type           VARCHAR(30)  NOT NULL,
    -- 書き手は US30（IT15）の CargoCancelledEvent。既定が業務上正しいので
    -- 空欄にはならない（ADR-0012 決定 3）。
    cancelled            BOOLEAN      NOT NULL DEFAULT FALSE,
    projected_at         TIMESTAMPTZ  NOT NULL,
    last_event_id        VARCHAR(36)
);

-- 予定の旅程。**時刻は写さない**（ADR-0012 決定 4）。荷役が要るのは
-- 「どの航海がどの港で積み降ろすか」だけで、予定の時刻は追跡側が持つ。
CREATE TABLE cargo_snapshot_leg (
    tracking_number  VARCHAR(25)  NOT NULL,
    leg_seq          INTEGER      NOT NULL,
    voyage_number    VARCHAR(20)  NOT NULL,
    load_unlocode    VARCHAR(5)   NOT NULL,
    unload_unlocode  VARCHAR(5)   NOT NULL,
    PRIMARY KEY (tracking_number, leg_seq),
    FOREIGN KEY (tracking_number) REFERENCES cargo_snapshot (tracking_number)
);

-- 荷役画面（S50）は航海番号を起点に「この船からこの港で降ろす貨物」を出す
-- （FindCargosOnVoyageQuery）。
CREATE INDEX idx_cargo_snapshot_leg_voyage
    ON cargo_snapshot_leg (voyage_number, unload_unlocode);
