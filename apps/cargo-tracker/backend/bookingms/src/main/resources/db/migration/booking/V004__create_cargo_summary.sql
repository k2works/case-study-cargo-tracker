-- 貨物予約の投影（data-model.md「booking_read_db」）。
--
-- shipper_name を非正規化して持つ。一覧が JOIN しないため。
--
-- 到着期限は DATE。期限当日に着く便は「間に合う」扱いなので、時刻付きで持つと
-- 素朴な比較で当日着を落とす。
--
-- 寸法の 3 列は集約の CargoSpecification.dimensions に対応する。US04 §受入基準 2 が
-- 「寸法」を求めるので、集約が持っていて投影が落とすと基準を満たせない。
--
-- 業務の CHECK は置かない（data-model.md 設計判断 1）。判断は集約が持ち、投影は
-- 写すだけ。両方に置くと、集約を直したときに投影だけが古い規則で弾く。
CREATE TABLE cargo_summary (
    booking_id             VARCHAR(36)  PRIMARY KEY,
    -- 画面に出す予約番号（US04 §受入基準 4）。booking_id は UUID なので人が読めない。
    -- 荷主コードと同じく投影側で採番する。
    booking_number         VARCHAR(20)  NOT NULL UNIQUE,
    shipper_id             VARCHAR(36)  NOT NULL,
    shipper_name           VARCHAR(200),
    tracking_number        VARCHAR(25)  UNIQUE,
    origin_unlocode        VARCHAR(5)   NOT NULL,
    destination_unlocode   VARCHAR(5)   NOT NULL,
    arrival_deadline       DATE         NOT NULL,
    cargo_type             VARCHAR(30)  NOT NULL,
    weight_kg              NUMERIC(12,2) NOT NULL,
    length_cm              NUMERIC(8,2),
    width_cm               NUMERIC(8,2),
    height_cm              NUMERIC(8,2),
    quantity               INTEGER      NOT NULL,
    product_name           VARCHAR(200) NOT NULL,
    hazard_imo_class       VARCHAR(20),
    hazard_un_number       VARCHAR(20),
    temperature_min_c      NUMERIC(5,2),
    temperature_max_c      NUMERIC(5,2),
    booking_status         VARCHAR(30)  NOT NULL,
    routing_status         VARCHAR(30)  NOT NULL,
    booked_at              TIMESTAMPTZ  NOT NULL,
    last_notified_at       TIMESTAMPTZ,
    confirmed_at           TIMESTAMPTZ,
    tracking_issued_at     TIMESTAMPTZ,
    last_handling_type     VARCHAR(30),
    last_handling_unlocode VARCHAR(5),
    last_handling_at       TIMESTAMPTZ,
    last_handling_off_route BOOLEAN,
    delivered_at           TIMESTAMPTZ,
    settled_at             TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    pending_cancellation   BOOLEAN      NOT NULL DEFAULT FALSE,
    projected_at           TIMESTAMPTZ  NOT NULL,
    last_event_id          VARCHAR(36)
);

-- shipper_id の索引は荷主向け一覧（FindShipperBookingsQuery・US18）の索引を兼ねる。
CREATE INDEX idx_cargo_summary_shipper ON cargo_summary (shipper_id);
CREATE INDEX idx_cargo_summary_booking_status ON cargo_summary (booking_status);
CREATE INDEX idx_cargo_summary_routing_status ON cargo_summary (routing_status);

-- 予約番号は投影側で採番する（荷主コードと同じ理由。集約で MAX+1 しない）。
CREATE SEQUENCE booking_number_seq START WITH 1 INCREMENT BY 1;
