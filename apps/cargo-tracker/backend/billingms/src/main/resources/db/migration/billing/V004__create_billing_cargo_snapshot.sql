-- 請求が読む貨物スナップショット（US21 / IT13）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「billing_read_db」
--
-- **同期問い合わせをしない。** 請求のたびに trackingms へ問い合わせると、
-- 相手が落ちている間は請求書が作れなくなる。契約イベント
-- TrackingInitializedEvent を購読して自分の読み取りモデルに写す
-- （ACL。shipper_contract_snapshot と同じ形・ADR-0012 と同じ形）。
--
-- **CargoDeliveredEvent では足りない。** 引渡は追跡番号・予約・時刻・場所しか
-- 運ばないので、区間も重量も貨物種別も分からない。料金の式が要るのはそちらである。
CREATE TABLE billing_cargo_snapshot (
    tracking_number      VARCHAR(25)  PRIMARY KEY,
    booking_id           VARCHAR(36)  NOT NULL,
    shipper_id           VARCHAR(36)  NOT NULL,
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    cargo_type           VARCHAR(30)  NOT NULL,
    -- **NULL を許す。** 重量を載せる前に積まれたイベントには入っていない
    -- （契約は追記専用で、過去のイベントは書き換えられない）。NULL のまま
    -- 算出しようとしたら断って要確認に出す——足りない重量で安い請求を
    -- 黙って出さない。
    weight_kg            NUMERIC(10,2),
    projected_at         TIMESTAMPTZ  NOT NULL,
    last_event_id        VARCHAR(36)
);

-- 予約から引く（引取済の予約に対して請求書を作るので、引きたいのは予約側から）。
CREATE INDEX idx_billing_cargo_snapshot_booking
    ON billing_cargo_snapshot (booking_id);

-- 実際に通った区間。**順序が業務の意味を持つ**（地域係数を区間ごとに数える）。
CREATE TABLE billing_cargo_leg (
    tracking_number VARCHAR(25) NOT NULL,
    leg_seq         INTEGER     NOT NULL,
    load_unlocode   VARCHAR(5)  NOT NULL,
    unload_unlocode VARCHAR(5)  NOT NULL,
    PRIMARY KEY (tracking_number, leg_seq),
    FOREIGN KEY (tracking_number)
        REFERENCES billing_cargo_snapshot (tracking_number) ON DELETE CASCADE
);
