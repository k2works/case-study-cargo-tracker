-- 横断の仕組み（冪等性キー、待避したイベント、予約番号の採番）。

-- 冪等性キー（ADR-003）。同じキーで内容が違う要求は request_hash で見分けて拒む。
CREATE TABLE platform.idempotency_key (
  principal_user_id platform.ulid  NOT NULL,
  idempotency_key   text           NOT NULL,
  command_name      text           NOT NULL,
  request_hash      text           NOT NULL,
  result            jsonb          NOT NULL,
  created_at        timestamptz    NOT NULL,
  expires_at        timestamptz    NOT NULL,   -- 保持は 7 日（NFR-D02）
  PRIMARY KEY (principal_user_id, idempotency_key),
  CONSTRAINT idempotency_key_not_blank CHECK (length(btrim(idempotency_key)) > 0)
);
CREATE INDEX idempotency_key_expires_at ON platform.idempotency_key (expires_at);

-- 投影が失敗したイベントを黙って捨てない（バックエンド 6.2）。件数を監視する。
CREATE TABLE platform.dead_letter_event (
  dead_letter_id  platform.ulid  PRIMARY KEY,
  projection_name text           NOT NULL,
  event_id        platform.ulid  NOT NULL,
  persistence_id  text           NOT NULL,
  seq_nr          bigint         NOT NULL,
  payload         bytea          NOT NULL,
  error           text           NOT NULL,
  attempts        int            NOT NULL CHECK (attempts >= 1),
  failed_at       timestamptz    NOT NULL,
  resolved_at     timestamptz
);
CREATE INDEX dead_letter_event_unresolved ON platform.dead_letter_event (failed_at) WHERE resolved_at IS NULL;

-- 予約番号の元。形式（接頭辞・桁数）は未定義で、整形はアプリケーションが持つ（Bolt 計画 Q-2）。
CREATE SEQUENCE platform.booking_number_seq START 1;
