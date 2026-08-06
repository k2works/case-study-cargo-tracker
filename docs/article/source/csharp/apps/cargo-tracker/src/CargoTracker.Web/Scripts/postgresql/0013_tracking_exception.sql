-- 追跡例外テーブル（PostgreSQL 方言・US19/US20・data-model Tracking Context）
-- 例外登録（遅延/破損/紛失）で tracking_exception_event を追記し、解決時に resolved_at/resolution_notes を更新する。
-- 集約内エンティティ（tracking_activity の子）のため version は持たず、集約ルート側の楽観ロックで保護する。
CREATE TABLE tracking_exception_event (
    id                  BIGSERIAL   PRIMARY KEY,
    tracking_id         BIGINT      NOT NULL REFERENCES tracking_activity(id),
    exception_type      VARCHAR(50) NOT NULL,
    location_unlocode   VARCHAR(5)  NOT NULL,
    occurred_at         TIMESTAMP   NOT NULL,
    escalation_flag     BOOLEAN     NOT NULL DEFAULT FALSE,
    description         VARCHAR(500),
    resolved_at         TIMESTAMP,
    resolution_notes    TEXT,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL
);
