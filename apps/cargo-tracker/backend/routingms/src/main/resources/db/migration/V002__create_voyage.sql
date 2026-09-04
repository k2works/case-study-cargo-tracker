-- 航海の投影（1 テーブル 1 ファイル。正典: docs/design/cargo-tracker/data-model.md）。
--
-- 航海番号は自然キー。departure_* / arrival_* は最初と最後の移動を非正規化する
-- （一覧の検索用。一覧は JOIN しない）。
-- 時刻は TIMESTAMPTZ。港のローカル時刻で入力・表示し、保存は絶対時刻（non_functional.md）。

CREATE TABLE voyage (
    voyage_number      VARCHAR(20)  PRIMARY KEY,
    carrier_code       VARCHAR(20)  NOT NULL,
    carrier_name       VARCHAR(100) NOT NULL,
    vessel_name        VARCHAR(100) NOT NULL,
    departure_unlocode VARCHAR(5)   NOT NULL,
    arrival_unlocode   VARCHAR(5)   NOT NULL,
    departure_at       TIMESTAMPTZ  NOT NULL,
    arrival_at         TIMESTAMPTZ  NOT NULL,
    cancelled          BOOLEAN      NOT NULL DEFAULT FALSE,
    registered_at      TIMESTAMPTZ  NOT NULL,
    projected_at       TIMESTAMPTZ  NOT NULL,
    last_event_id      VARCHAR(36)
);

CREATE INDEX idx_voyage_departure ON voyage (departure_unlocode, departure_at);
CREATE INDEX idx_voyage_arrival ON voyage (arrival_unlocode, arrival_at);
