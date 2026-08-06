-- 追跡テーブル（PostgreSQL 方言・US14/US15/US17・data-model IT5 実装状況）
-- 追跡番号発行（US14）で tracking_activity を作成し、荷役・手動更新で tracking_handling_event を追記する。
CREATE TABLE tracking_activity (
    id                  BIGSERIAL   PRIMARY KEY,
    tracking_number     VARCHAR(20) NOT NULL,
    booking_id          VARCHAR(20) NOT NULL,
    transport_status    VARCHAR(30) NOT NULL DEFAULT 'NOT_RECEIVED',
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_tracking_activity_number UNIQUE (tracking_number),
    CONSTRAINT uk_tracking_activity_booking UNIQUE (booking_id)
);

CREATE TABLE tracking_handling_event (
    id                  BIGSERIAL   PRIMARY KEY,
    tracking_id         BIGINT      NOT NULL REFERENCES tracking_activity(id),
    seq_number          INTEGER     NOT NULL,
    event_type          VARCHAR(30) NOT NULL,
    event_time          TIMESTAMP   NOT NULL,
    location_unlocode   VARCHAR(5)  NOT NULL,
    voyage_number       VARCHAR(20),
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    CONSTRAINT uk_tracking_event_seq UNIQUE (tracking_id, seq_number),
    CONSTRAINT ck_tracking_event_seq_positive CHECK (seq_number >= 1)
);
