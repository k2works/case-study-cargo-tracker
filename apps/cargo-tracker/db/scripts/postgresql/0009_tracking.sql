-- tracking_activity / tracking_handling_event（貨物追跡・US14/US15/US17/US18）。PostgreSQL 方言。
-- 状態はイベント履歴からの導出値だが、クエリ用に transport_status を非正規化保持する。
-- access_token は公開追跡ページ（US18・未認証）用の推測困難トークン。
CREATE TABLE tracking_activity (
    id               BIGSERIAL   PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL UNIQUE,
    booking_id       VARCHAR(20) NOT NULL,
    transport_status VARCHAR(30) NOT NULL DEFAULT 'NOT_RECEIVED',
    access_token     VARCHAR(64) NOT NULL UNIQUE,
    created_at       TEXT   NOT NULL DEFAULT (now())::text,
    updated_at       TEXT   NOT NULL DEFAULT (now())::text,
    version          BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE tracking_handling_event (
    id                BIGSERIAL   PRIMARY KEY,
    tracking_id       BIGINT      NOT NULL REFERENCES tracking_activity(id),
    event_type        VARCHAR(30) NOT NULL,
    event_time        TEXT   NOT NULL,
    location_unlocode VARCHAR(5),
    voyage_number     VARCHAR(20),
    seq_number        INTEGER     NOT NULL,
    created_at        TEXT   NOT NULL DEFAULT (now())::text,
    updated_at        TEXT   NOT NULL DEFAULT (now())::text
);

CREATE INDEX idx_tracking_handling_event_tracking_id ON tracking_handling_event(tracking_id);
