-- 要確認一覧（S70）。bookingms と同じ形。
--
-- 投影が一意制約で弾いたもの・連鎖が補償に至ったものを担当ロールに見せる。
-- 追記専用でリプレイでも消さないため、識別子は内容から導く（AttentionItemRecorder）。

CREATE TABLE attention_item (
    item_id         VARCHAR(36)  PRIMARY KEY,
    kind            VARCHAR(30)  NOT NULL,
    target_type     VARCHAR(30)  NOT NULL,
    target_id       VARCHAR(36)  NOT NULL,
    assigned_role   VARCHAR(30)  NOT NULL,
    reason          VARCHAR(200) NOT NULL,
    payload         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(50)
);

CREATE INDEX idx_attention_item_role_open
    ON attention_item (assigned_role, occurred_at)
    WHERE acknowledged_at IS NULL;
