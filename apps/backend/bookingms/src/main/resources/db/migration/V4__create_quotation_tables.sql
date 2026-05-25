-- 見積（Quotation）Read Model。予約前段階の見積とルート候補を保持する（US01）。
CREATE TABLE IF NOT EXISTS quotation (
    quotation_id         VARCHAR(36)   NOT NULL PRIMARY KEY,
    shipper_id           VARCHAR(36)   NOT NULL,
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    arrival_deadline     DATE          NOT NULL,
    cargo_type           VARCHAR(16)   NOT NULL,
    weight_kg            NUMERIC(12,2),
    estimated_amount     NUMERIC(14,2),
    estimated_currency   VARCHAR(3),
    valid_until          DATE          NOT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0
);

-- 見積のルート候補。quotation に従属する。
CREATE TABLE IF NOT EXISTS quotation_candidate (
    quotation_id       VARCHAR(36)   NOT NULL,
    candidate_seq      INTEGER       NOT NULL,
    estimated_days     INTEGER       NOT NULL,
    estimated_cost     NUMERIC(14,2) NOT NULL,
    estimated_currency VARCHAR(3)    NOT NULL,
    itinerary_summary  VARCHAR(1000),
    PRIMARY KEY (quotation_id, candidate_seq)
);

CREATE INDEX IF NOT EXISTS idx_quotation_shipper_status
    ON quotation (shipper_id, status);
