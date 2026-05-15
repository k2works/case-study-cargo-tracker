-- V004: quotation / quotation_candidate Read Model（US01 輸送見積）
--
-- Quotation Aggregate（Axon Event Sourcing）の Read Model。
-- Event Store の QuotationCreatedEvent を QuotationProjectionsEventHandler が
-- 購読し、ここに INSERT する。
--
-- 設計準拠: docs/design/data-model.md L350-376 の Booking Read Model セクション
-- shipper_id は既存 shipper.id (BIGINT) と FK 整合（cargo_summary と同じ設計判断）。
-- data-model.md では VARCHAR(36) だが IT2 実装に揃える（将来の data-model 同期 ADR で再評価）。

CREATE TABLE IF NOT EXISTS quotation (
    quotation_id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    shipper_id            BIGINT        NOT NULL,
    origin_unlocode       VARCHAR(5)    NOT NULL,
    destination_unlocode  VARCHAR(5)    NOT NULL,
    arrival_deadline      DATE          NOT NULL,
    cargo_type            VARCHAR(16)   NOT NULL,
    weight_kg             DECIMAL(12,2) NOT NULL,
    estimated_amount      DECIMAL(14,2),
    estimated_currency    VARCHAR(3),
    valid_until           DATE          NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    -- 危険物見積の場合に IMO クラス・UN 番号を保持（任意）
    hazard_imo_class      VARCHAR(20),
    hazard_un_number      VARCHAR(20),
    hazard_declaration    TEXT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_quotation_shipper_status
    ON quotation (shipper_id, status);

CREATE TABLE IF NOT EXISTS quotation_candidate (
    quotation_id          VARCHAR(36)   NOT NULL,
    candidate_seq         INTEGER       NOT NULL,
    estimated_days        INTEGER       NOT NULL,
    estimated_cost        DECIMAL(14,2) NOT NULL,
    estimated_currency    VARCHAR(3)    NOT NULL,
    itinerary_summary     TEXT,
    voyage_numbers        VARCHAR(255),
    PRIMARY KEY (quotation_id, candidate_seq),
    FOREIGN KEY (quotation_id) REFERENCES quotation (quotation_id) ON DELETE CASCADE
);
