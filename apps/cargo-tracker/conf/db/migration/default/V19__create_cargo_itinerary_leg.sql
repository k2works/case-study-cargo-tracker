-- IT7 0.14 / O3: cargo_itinerary の航海番号文字列 (CSV 形式) を区間別の正式テーブルへ展開する。
-- 1 cargo に対し N 個の leg を seq_number 順で保持する。
CREATE TABLE cargo_itinerary_leg (
  id BIGSERIAL PRIMARY KEY,
  cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE CASCADE,
  seq_number INTEGER NOT NULL,
  voyage_number VARCHAR(20) NOT NULL,
  from_location_unlocode VARCHAR(5) NOT NULL,
  to_location_unlocode VARCHAR(5) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (cargo_id, seq_number)
);

CREATE INDEX idx_cargo_itinerary_leg_cargo ON cargo_itinerary_leg (cargo_id);
CREATE INDEX idx_cargo_itinerary_leg_voyage ON cargo_itinerary_leg (voyage_number);

COMMENT ON TABLE cargo_itinerary_leg IS '貨物経路の区間別詳細 (IT7 0.14 / O3 解消)';
COMMENT ON COLUMN cargo_itinerary_leg.seq_number IS 'leg 順序 (1 始まり)';
COMMENT ON COLUMN cargo_itinerary_leg.from_location_unlocode IS '当該 leg 出発地';
COMMENT ON COLUMN cargo_itinerary_leg.to_location_unlocode IS '当該 leg 到着地';
