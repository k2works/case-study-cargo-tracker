-- migrate:up

-- IT3 US27 通関情報を予約に紐付ける
--
-- iteration_plan-3.md / data-model.md の方針:
--   - 既存 customs_declaration テーブルを拡張する想定だったが、
--     Handling Context が未実装 (handling_activity テーブル不在) のため
--     IT3 では US27 が必要とする最小カラムで新規作成する。
--   - 将来 Handling Context 実装時に handling_activity_id 等を追加する
--     マイグレーションを別途投入する (本テーブルはその時 ALTER で拡張可能)。
--
-- 1 予約 = 0..1 通関情報 のため booking_id に UNIQUE を付与し、
-- AttachCustomsDeclarationCommand から ON CONFLICT (booking_id) DO UPDATE で
-- upsert できるようにする。

CREATE TABLE customs_declaration (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          VARCHAR(20)  NOT NULL UNIQUE,
    hs_code             VARCHAR(10)  NOT NULL
        CHECK (char_length(hs_code) BETWEEN 6 AND 10 AND hs_code ~ '^[0-9]+$'),
    broker_name         VARCHAR(100) NOT NULL
        CHECK (char_length(broker_name) BETWEEN 1 AND 100),
    declaration_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (declaration_status IN ('PENDING', 'CLEARED', 'HELD', 'REJECTED')),
    version             BIGINT       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customs_declaration_booking ON customs_declaration (booking_id);
CREATE INDEX idx_customs_declaration_status ON customs_declaration (declaration_status);

COMMENT ON TABLE customs_declaration IS
  '通関申告 (US27 IT3)。1 予約 = 0..1 通関情報。Handling Context 実装時に handling_activity_id 等を ALTER で追加予定。';
COMMENT ON COLUMN customs_declaration.hs_code IS
  'Harmonized System code (6-10 桁の数字)。';
COMMENT ON COLUMN customs_declaration.declaration_status IS
  'PENDING / CLEARED / HELD / REJECTED。状態遷移ルールは現状アプリ側で強制しない。';

-- migrate:down

DROP INDEX IF EXISTS idx_customs_declaration_status;
DROP INDEX IF EXISTS idx_customs_declaration_booking;
DROP TABLE IF EXISTS customs_declaration;
