-- V011: 貨物例外テーブルに推定到着日カラムを追加（DELAY 種別の新しい到着予定日）
ALTER TABLE cargo_exceptions ADD COLUMN estimated_arrival_date DATE;
