-- Stripe webhook 冪等性管理テーブル（IT9 A1.2 / ADR-0020 / US26）
-- 同一 Stripe Event ID（evt_xxx）の重複受信を抑止する。
-- data-model.md L737-750 webhook_processed と整合。
-- received_at は受信時、processed_at は後続処理完了時。

CREATE TABLE IF NOT EXISTS webhook_processed (
    event_id           VARCHAR(64)  NOT NULL,
    provider           VARCHAR(20)  NOT NULL,                  -- STRIPE 等
    event_type         VARCHAR(64)  NOT NULL,                  -- payment_intent.succeeded 等
    invoice_id         VARCHAR(36),                            -- 関連請求書（webhook 受信時点では NULL 可、A1.4 で関連付け）
    payload_hash       VARCHAR(64)  NOT NULL,                  -- SHA-256 of raw payload（改ざん検知）
    processing_status  VARCHAR(20)  NOT NULL,                  -- RECEIVED / PROCESSED / FAILED
    received_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at       TIMESTAMP,
    error_reason       TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_processed_provider_received
    ON webhook_processed (provider, received_at);
CREATE INDEX IF NOT EXISTS idx_webhook_processed_invoice
    ON webhook_processed (invoice_id);
CREATE INDEX IF NOT EXISTS idx_webhook_processed_status_received
    ON webhook_processed (processing_status, received_at);
