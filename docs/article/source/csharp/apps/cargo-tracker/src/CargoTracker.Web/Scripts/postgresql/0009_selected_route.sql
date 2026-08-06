-- 確定経路テーブル（PostgreSQL 方言・US09・data-model IT4 実装状況）
-- 経路設計者が経路候補から選択・確定した経路を予約単位で保持する。
CREATE TABLE selected_route (
    id              BIGSERIAL      PRIMARY KEY,
    booking_id      VARCHAR(20)    NOT NULL,
    transit_days    INTEGER        NOT NULL,
    cost            NUMERIC(15,2)  NOT NULL,
    route_status    VARCHAR(20)    NOT NULL DEFAULT 'CONFIRMED',
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_selected_route_booking UNIQUE (booking_id)
);

CREATE TABLE selected_route_leg (
    id                  BIGSERIAL   PRIMARY KEY,
    selected_route_id   BIGINT      NOT NULL REFERENCES selected_route(id),
    seq_number          INTEGER     NOT NULL,
    voyage_number       VARCHAR(20) NOT NULL,
    board_unlocode      VARCHAR(5)  NOT NULL,
    alight_unlocode     VARCHAR(5)  NOT NULL,
    board_time          TIMESTAMP   NOT NULL,
    alight_time         TIMESTAMP   NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    CONSTRAINT uk_selected_route_leg_seq UNIQUE (selected_route_id, seq_number),
    CONSTRAINT ck_selected_route_leg_seq_positive CHECK (seq_number >= 1)
);
