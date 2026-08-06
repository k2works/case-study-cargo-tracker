-- tracking_exception_event（追跡例外イベント・US19 遅延 / US20 破損・紛失）。PostgreSQL 方言。
-- ExceptionResolution DU（Unresolved of escalated / Resolved of resolvedAt）を
-- escalation_flag（BOOLEAN）＋ resolved_at（NULL 可）の 2 カラムへ写像する。
-- 読み出し時、resolved_at が NULL なら Unresolved escalation_flag、非 NULL なら Resolved resolved_at に復元する。
CREATE TABLE tracking_exception_event (
    id               BIGSERIAL   PRIMARY KEY,
    tracking_id      BIGINT      NOT NULL REFERENCES tracking_activity(id),
    exception_type   VARCHAR(50) NOT NULL,
    location_unlocode VARCHAR(5),
    occurred_at      TEXT NOT NULL,
    escalation_flag  BOOLEAN     NOT NULL DEFAULT FALSE,
    description      VARCHAR(500),
    resolved_at      TEXT,
    resolution_notes TEXT,
    seq_number       INTEGER     NOT NULL,
    created_at       TEXT   NOT NULL DEFAULT (now())::text,
    updated_at       TEXT   NOT NULL DEFAULT (now())::text
);

CREATE INDEX idx_tracking_exception_event_tracking_id ON tracking_exception_event(tracking_id);
