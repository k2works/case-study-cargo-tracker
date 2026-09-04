-- 航海内の港間移動。経路探索の VoyageGraph はこのテーブルから組む（US08 / IT5）。
-- 更新（US25）では全行を入れ替える。

CREATE TABLE carrier_movement (
    voyage_number      VARCHAR(20) NOT NULL REFERENCES voyage (voyage_number),
    movement_seq       INTEGER     NOT NULL,
    departure_unlocode VARCHAR(5)  NOT NULL,
    arrival_unlocode   VARCHAR(5)  NOT NULL,
    departure_at       TIMESTAMPTZ NOT NULL,
    arrival_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (voyage_number, movement_seq)
);
