-- handlingms Read Model（IT5 US15・US16）
-- data-model.md handling_read_db の ER 図と整合。
-- 監査カラム（created_at / updated_at / version）は他サービスと同様に明示する。

-- handling_activity: HandlingActivity 集約の Read Model。
-- CargoSnapshot ACL（IT5 タスク 3.1）の内容をフラットに展開して保持する。
-- Booking Context の cargo_summary を直接 JOIN しないことが設計方針。
CREATE TABLE IF NOT EXISTS handling_activity (
    activity_id          VARCHAR(36)  NOT NULL,
    -- CargoSnapshot ACL の射影
    booking_id           VARCHAR(36)  NOT NULL,
    tracking_number      VARCHAR(25)  NOT NULL,
    origin_unlocode      VARCHAR(5)   NOT NULL,
    destination_unlocode VARCHAR(5)   NOT NULL,
    cargo_type           VARCHAR(16)  NOT NULL,
    -- 荷役作業本体
    handling_type        VARCHAR(16)  NOT NULL,  -- RECEIVE / LOAD / UNLOAD / CLAIM / CUSTOMS
    occurred_at          TIMESTAMP    NOT NULL,
    recorded_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlocode             VARCHAR(5)   NOT NULL,
    voyage_number        VARCHAR(20),            -- LOAD / UNLOAD は必須（不変条件）
    handler_id           VARCHAR(36)  NOT NULL,
    -- フラグ
    unexpected           BOOLEAN      NOT NULL DEFAULT FALSE,  -- 予定外の場所/種別を検知
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (activity_id)
);

CREATE INDEX IF NOT EXISTS idx_handling_activity_tracking_occurred ON handling_activity (tracking_number, occurred_at);
CREATE INDEX IF NOT EXISTS idx_handling_activity_voyage ON handling_activity (voyage_number);
CREATE INDEX IF NOT EXISTS idx_handling_activity_handler ON handling_activity (handler_id);

-- handling_itinerary_snapshot: CargoSnapshot ACL が保持する確定旅程のスナップショット。
-- handling_activity 1 件につき 0..n 件の輸送区間を持つ（cargo_leg の射影）。
CREATE TABLE IF NOT EXISTS handling_itinerary_snapshot (
    activity_id     VARCHAR(36) NOT NULL,
    leg_seq         INTEGER     NOT NULL,
    voyage_number   VARCHAR(20) NOT NULL,
    load_unlocode   VARCHAR(5)  NOT NULL,
    unload_unlocode VARCHAR(5)  NOT NULL,
    load_at         TIMESTAMP   NOT NULL,
    unload_at       TIMESTAMP   NOT NULL,
    PRIMARY KEY (activity_id, leg_seq)
);

-- claim_verification: 引取時（handling_type = CLAIM）の荷受人確認情報（US16 不変条件）
-- signature_ref または confirmation_code のいずれかが必須（data-model.md CHECK 制約）。
CREATE TABLE IF NOT EXISTS claim_verification (
    activity_id       VARCHAR(36)  NOT NULL,
    consignee_name    VARCHAR(200) NOT NULL,
    signature_ref     VARCHAR(200),
    confirmation_code VARCHAR(50),
    verified_at       TIMESTAMP    NOT NULL,
    PRIMARY KEY (activity_id),
    CONSTRAINT chk_claim_verification_means
        CHECK (signature_ref IS NOT NULL OR confirmation_code IS NOT NULL)
);
