-- 確定経路テーブル（SQLite 方言・US09・data-model IT4 実装状況）
-- 経路設計者が経路候補から選択・確定した経路を予約単位で保持する。
CREATE TABLE selected_route (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id      TEXT    NOT NULL,
    transit_days    INTEGER NOT NULL,
    cost            TEXT    NOT NULL,
    route_status    TEXT    NOT NULL DEFAULT 'CONFIRMED',
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_selected_route_booking UNIQUE (booking_id)
);

CREATE TABLE selected_route_leg (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    selected_route_id   INTEGER NOT NULL REFERENCES selected_route(id),
    seq_number          INTEGER NOT NULL,
    voyage_number       TEXT    NOT NULL,
    board_unlocode      TEXT    NOT NULL,
    alight_unlocode     TEXT    NOT NULL,
    board_time          TEXT    NOT NULL,
    alight_time         TEXT    NOT NULL,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    CONSTRAINT uk_selected_route_leg_seq UNIQUE (selected_route_id, seq_number),
    CONSTRAINT ck_selected_route_leg_seq_positive CHECK (seq_number >= 1)
);
