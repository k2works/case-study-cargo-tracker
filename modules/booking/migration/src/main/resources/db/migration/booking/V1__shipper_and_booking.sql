-- U-02 貨物予約のスキーマ（Bolt 1：US02・US04 の基本形）。
-- 各 Bolt で必要になった列・表だけを作る。法人の列・経路・追跡番号・キャンセルの列は後の Bolt で足す。

CREATE SCHEMA IF NOT EXISTS booking;

-- 荷主（書き込み側の表。イベントソーシングにしない。ADR-004）。個人情報はこの表にだけ置く。
CREATE TABLE booking.shipper (
  shipper_id       platform.ulid PRIMARY KEY,
  shipper_type     text          NOT NULL CHECK (shipper_type = 'INDIVIDUAL'),   -- 法人は Bolt 13 で CHECK を広げる
  name             text          NOT NULL CHECK (length(btrim(name)) > 0),
  address          text          NOT NULL CHECK (length(btrim(address)) > 0),
  contact          text          NOT NULL CHECK (length(btrim(contact)) > 0),
  email            text          NOT NULL,
  email_normalized text          NOT NULL,  -- 小文字化して重複の検出に使う。一意制約にしない（US02 AC-02）
  deleted_at       timestamptz,             -- 論理削除（DB-06）
  created_at       timestamptz   NOT NULL,
  created_by       platform.ulid NOT NULL,
  updated_at       timestamptz   NOT NULL,
  updated_by       platform.ulid NOT NULL,
  CONSTRAINT shipper_email_format CHECK (email_normalized ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')
);
CREATE INDEX shipper_email_normalized ON booking.shipper (email_normalized);

-- 貨物予約（読み取りモデル。1 予約 1 行）。
CREATE TABLE booking.booking_view (
  booking_id               platform.ulid       PRIMARY KEY,
  booking_number           text                NOT NULL UNIQUE,
  shipper_id               platform.ulid       NOT NULL REFERENCES booking.shipper (shipper_id),
  cargo_kind               platform.cargo_kind NOT NULL CHECK (cargo_kind = 'GENERAL'),  -- 危険物・冷凍冷蔵は Bolt 13
  item_name                text                NOT NULL CHECK (length(btrim(item_name)) > 0),
  weight_kg                numeric(12, 3)      NOT NULL CHECK (weight_kg > 0),
  length_cm                numeric(8, 2)       NOT NULL CHECK (length_cm > 0),
  width_cm                 numeric(8, 2)       NOT NULL CHECK (width_cm > 0),
  height_cm                numeric(8, 2)       NOT NULL CHECK (height_cm > 0),
  quantity                 int                 NOT NULL CHECK (quantity >= 1),
  origin_port              platform.port_code  NOT NULL REFERENCES reference.port (port_code),
  destination_port         platform.port_code  NOT NULL REFERENCES reference.port (port_code),
  preferred_handover_date  date                NOT NULL,
  deadline                 date                NOT NULL,
  status                   text                NOT NULL CHECK (status IN
    ('RECEIVED', 'ROUTE_PROPOSAL', 'CONFIRMED', 'TRACKING_ISSUED', 'IN_TRANSIT', 'DELIVERED', 'SETTLED', 'CANCELLED')),
  available_commands       text[]              NOT NULL,   -- 状態から、ドメインの述語で計算（DM-10）
  aggregate_version        bigint              NOT NULL CHECK (aggregate_version >= 1),
  last_event_id            platform.ulid       NOT NULL,
  created_at               timestamptz         NOT NULL,
  updated_at               timestamptz         NOT NULL,
  CONSTRAINT booking_ports_differ CHECK (origin_port <> destination_port),
  CONSTRAINT booking_handover_before_deadline CHECK (preferred_handover_date <= deadline)
);
CREATE INDEX booking_view_status_updated ON booking.booking_view (status, updated_at DESC);
CREATE INDEX booking_view_shipper ON booking.booking_view (shipper_id);

-- 予約の履歴（追記専用）。イベント ID を主キーにして、再投影で行が増えない。
CREATE TABLE booking.booking_history (
  event_id       platform.ulid PRIMARY KEY,
  booking_id     platform.ulid NOT NULL REFERENCES booking.booking_view (booking_id),
  occurred_at    timestamptz   NOT NULL,
  actor_user_id  platform.ulid,            -- システムの反応は NULL
  kind           text          NOT NULL CHECK (kind IN
    ('REGISTERED', 'CORRECTED', 'ROUTE_DESIGN_REQUESTED', 'ROUTE_ATTACHED', 'ROUTE_DETACHED', 'CONFIRMED',
     'TRACKING_ISSUED', 'TRANSIT_STARTED', 'ROUTE_REPLACED', 'DELIVERED', 'SETTLED', 'CANCELLED',
     'CANCELLATION_REQUESTED', 'CANCELLATION_APPROVED', 'CANCELLATION_REJECTED')),
  reason         text,
  detail         jsonb
);
CREATE INDEX booking_history_booking_occurred ON booking.booking_history (booking_id, occurred_at);
