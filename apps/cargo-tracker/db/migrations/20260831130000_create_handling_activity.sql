-- migrate:up

-- IT5 US15 荷役作業を記録する - Handling Context の中核テーブル
--
-- data-model.md §handling_activity に準拠。
-- 集約構造: 1 予約 = 0..* 荷役イベント (時系列で記録)
--
-- ADR-0004 Cross-BC 規約: booking_id は業務キー Text で保持 (Booking BC の
-- cargo.booking_id を参照)。DB 制約は付与せず、Application 層で存在確認する。

CREATE TABLE handling_activity (
    id                     BIGSERIAL PRIMARY KEY,
    booking_id             VARCHAR(20) NOT NULL,
    event_type             VARCHAR(30) NOT NULL
                           CHECK (event_type IN ('RECEIVE','LOAD','UNLOAD','CUSTOMS','CLAIM')),
    event_completion_time  TIMESTAMPTZ NOT NULL,
    location_unlocode      VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    voyage_number          VARCHAR(20),
    operator_name          VARCHAR(200) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_handling_activity_booking ON handling_activity (booking_id);
CREATE INDEX idx_handling_activity_time    ON handling_activity (event_completion_time DESC);

-- LOAD/UNLOAD 時は voyage_number 必須 (CHECK 制約でアプリ層検証を補強)
ALTER TABLE handling_activity ADD CONSTRAINT chk_voyage_number_for_load_unload
  CHECK (
    event_type NOT IN ('LOAD','UNLOAD') OR voyage_number IS NOT NULL
  );

-- migrate:down

DROP TABLE handling_activity;
