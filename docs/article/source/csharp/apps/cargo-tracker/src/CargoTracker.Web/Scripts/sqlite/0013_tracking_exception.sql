-- 追跡例外テーブル（SQLite 方言・US19/US20・data-model Tracking Context）
-- 例外登録（遅延/破損/紛失）で tracking_exception_event を追記し、解決時に resolved_at/resolution_notes を更新する。
-- 集約内エンティティ（tracking_activity の子）のため version は持たず、集約ルート側の楽観ロックで保護する。
CREATE TABLE tracking_exception_event (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_id         INTEGER NOT NULL REFERENCES tracking_activity(id),
    exception_type      TEXT    NOT NULL,
    location_unlocode   TEXT    NOT NULL,
    occurred_at         TEXT    NOT NULL,
    escalation_flag     INTEGER NOT NULL DEFAULT 0,
    description         TEXT,
    resolved_at         TEXT,
    resolution_notes    TEXT,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL
);
