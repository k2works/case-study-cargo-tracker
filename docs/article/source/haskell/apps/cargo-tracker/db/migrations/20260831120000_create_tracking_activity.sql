-- migrate:up

-- IT5 US14 追跡番号を発行する - Tracking Context の中核テーブル
--
-- data-model.md §tracking_activity に準拠。追跡活動は 1 予約に 0..1 で対応し、
-- BookingConfirmed イベント購読で IssueTrackingNumberCommand が新規レコードを INSERT する。
--
-- 集約構造:
--   tracking_activity 1 -- 0..* tracking_handling_event (荷役履歴、US15/US16 で追加)
--   tracking_activity 1 -- 0..* tracking_exception_event (US19/US20 で追加)
--   tracking_activity 1 -- 0..1 confirmation_code (US16 引取確認、既存 migration 参照)
--
-- transport_status は導出値のフォールバック用に持つ (通常は tracking_handling_event
-- から currentStatus で導出、DB カラムは cached view として扱う)。

CREATE TABLE tracking_activity (
    id                BIGSERIAL PRIMARY KEY,
    tracking_number   VARCHAR(20) NOT NULL UNIQUE,
    booking_id        VARCHAR(20) NOT NULL,
    transport_status  VARCHAR(30) NOT NULL DEFAULT 'TsNotReceived'
                      CHECK (transport_status IN (
                        'TsNotReceived','TsReceived','TsLoaded','TsOnboardCarrier',
                        'TsUnloaded','TsAwaitingClaim','TsClaimed','TsInException','TsUnknown'
                      )),
    version           INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_activity_booking ON tracking_activity (booking_id);

-- migrate:down

DROP TABLE tracking_activity;
