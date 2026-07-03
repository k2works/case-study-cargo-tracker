-- migrate:up

-- IT7 US17 貨物状態を手動更新する - 監査ログテーブル
--
-- iteration_plan-7.md §5.1-5.3 に対応。
-- Tracker/Admin が Tracking 状態を手動修正する際、前後の状態と理由・変更者・
-- 時刻を追記型で記録する。監査目的のためレコードは変更不可 (UPDATE/DELETE
-- なし想定、SELECT + INSERT のみ)。
--
-- Domain: Cargotracker.Tracking.Domain.Model.TrackingStateAudit
-- Port: Cargotracker.Tracking.Application.TrackingStateAuditPorts
-- Command: Cargotracker.Tracking.Application.ManualStateUpdateCommand

CREATE TABLE tracking_state_audit (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL,
    previous_status  VARCHAR(30) NOT NULL,
    new_status       VARCHAR(30) NOT NULL,
    reason           TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    changed_by       VARCHAR(64) NOT NULL,
    changed_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT tsa_state_change CHECK (previous_status <> new_status),
    CONSTRAINT tsa_tracking_number_format
        CHECK (tracking_number ~ '^[A-Z0-9]{8}$')
);

CREATE INDEX idx_tsa_tracking_number_changed_at
    ON tracking_state_audit (tracking_number, changed_at DESC);
CREATE INDEX idx_tsa_changed_by
    ON tracking_state_audit (changed_by);

-- migrate:down

DROP TABLE tracking_state_audit;
