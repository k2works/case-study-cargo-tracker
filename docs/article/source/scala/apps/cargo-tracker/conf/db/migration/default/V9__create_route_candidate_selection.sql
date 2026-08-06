-- IT4 タスク 1.2 / US09: 経路選択集約の永続化テーブル（ADR 0009）。
-- 1 予約 1 選択を booking_id UNIQUE 制約で保証する。
-- voyage_numbers はカンマ区切り（順序保持）。N:N が必要になれば別テーブルへ正規化。

CREATE TABLE route_candidate_selection (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      VARCHAR(20) NOT NULL UNIQUE,
    voyage_numbers  VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_route_candidate_selection_status
        CHECK (status IN ('Pending', 'Confirmed'))
);

CREATE INDEX idx_route_candidate_selection_booking ON route_candidate_selection (booking_id);
