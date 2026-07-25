-- Estimation Context: ルート候補に経由港（CSV）を追加（T3・US08 実経路化）。
ALTER TABLE route_candidate
    ADD COLUMN waypoints VARCHAR(200) NOT NULL DEFAULT '';
