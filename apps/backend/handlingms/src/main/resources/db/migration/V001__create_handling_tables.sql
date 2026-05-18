-- V001: handling_activity / handling_itinerary_snapshot / claim_verification の Read Model 作成
--
-- HandlingActivity Aggregate（Axon Event Sourcing）の Read Model。
-- Event Store の HandlingActivityRegisteredEvent / CargoStatusUpdatedEvent を
-- HandlingProjectionsEventHandler が購読し、ここに INSERT/UPDATE する。
--
-- 設計準拠: docs/design/data-model.md L600-619（handling_activity）
--          docs/design/domain-model.md L782-822（HandlingActivity Aggregate）
-- 関連 ADR: ADR-0012 handlingms と trackingms の責務分離

CREATE TABLE IF NOT EXISTS handling_activity (
    activity_id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    -- CargoSnapshot ACL の射影（Booking Context への依存を腐敗防止層で隔離）
    booking_id            VARCHAR(36)   NOT NULL,
    tracking_number       VARCHAR(25)   NOT NULL,
    origin_unlocode       VARCHAR(5)    NOT NULL,
    destination_unlocode  VARCHAR(5)    NOT NULL,
    cargo_type            VARCHAR(16)   NOT NULL,
    -- 荷役作業本体
    handling_type         VARCHAR(16)   NOT NULL,
    occurred_at           TIMESTAMP     NOT NULL,
    recorded_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlocode              VARCHAR(5)    NOT NULL,
    voyage_number         VARCHAR(20),
    handler_id            VARCHAR(36)   NOT NULL,
    -- フラグ
    unexpected            BOOLEAN       NOT NULL DEFAULT FALSE,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_handling_activity_handling_type
        CHECK (handling_type IN ('RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS')),
    CONSTRAINT chk_handling_activity_cargo_type
        CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED'))
);

-- インデックス（data-model.md L656-659 準拠）
CREATE INDEX IF NOT EXISTS idx_handling_activity_tracking_number_occurred_at
    ON handling_activity (tracking_number, occurred_at);
CREATE INDEX IF NOT EXISTS idx_handling_activity_voyage_number
    ON handling_activity (voyage_number);
CREATE INDEX IF NOT EXISTS idx_handling_activity_handler_id
    ON handling_activity (handler_id);

-- LOAD/UNLOAD 時の旅程スナップショット
CREATE TABLE IF NOT EXISTS handling_itinerary_snapshot (
    activity_id      VARCHAR(36) NOT NULL,
    leg_seq          INTEGER     NOT NULL,
    voyage_number    VARCHAR(20) NOT NULL,
    load_unlocode    VARCHAR(5)  NOT NULL,
    unload_unlocode  VARCHAR(5)  NOT NULL,
    load_at          TIMESTAMP   NOT NULL,
    unload_at        TIMESTAMP   NOT NULL,
    PRIMARY KEY (activity_id, leg_seq),
    CONSTRAINT fk_handling_itinerary_snapshot_activity
        FOREIGN KEY (activity_id) REFERENCES handling_activity(activity_id) ON DELETE CASCADE
);

-- 引取確認（CLAIM 時のみ作成、US16）
CREATE TABLE IF NOT EXISTS claim_verification (
    activity_id        VARCHAR(36)  NOT NULL PRIMARY KEY,
    consignee_name     VARCHAR(200) NOT NULL,
    signature_ref      VARCHAR(200),
    confirmation_code  VARCHAR(50),
    verified_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_claim_verification_activity
        FOREIGN KEY (activity_id) REFERENCES handling_activity(activity_id) ON DELETE CASCADE,
    CONSTRAINT chk_claim_verification_method
        CHECK (signature_ref IS NOT NULL OR confirmation_code IS NOT NULL)
);

-- CargoSnapshot ACL: Booking Context の CargoBookedEvent / CargoRoutedEvent を購読して維持
-- （ADR-0012 で定義した責務分離に従い、Booking 集約への直接参照は禁止）
CREATE TABLE IF NOT EXISTS cargo_snapshot (
    booking_id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    tracking_number       VARCHAR(25),
    origin_unlocode       VARCHAR(5)    NOT NULL,
    destination_unlocode  VARCHAR(5)    NOT NULL,
    cargo_type            VARCHAR(16)   NOT NULL,
    arrival_deadline      DATE          NOT NULL,
    booking_status        VARCHAR(20)   NOT NULL,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cargo_snapshot_cargo_type
        CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED'))
);

CREATE INDEX IF NOT EXISTS idx_cargo_snapshot_tracking_number
    ON cargo_snapshot (tracking_number);

-- US17 暫定: 貨物状態手動更新の履歴（IT6 で trackingms に移管予定）
CREATE TABLE IF NOT EXISTS cargo_status_history (
    history_id        VARCHAR(36)   NOT NULL PRIMARY KEY,
    tracking_number   VARCHAR(25)   NOT NULL,
    new_status        VARCHAR(20)   NOT NULL,
    unlocode          VARCHAR(5)    NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    operator_id       VARCHAR(36)   NOT NULL,
    recorded_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cargo_status_history_tracking_number
    ON cargo_status_history (tracking_number, updated_at);
