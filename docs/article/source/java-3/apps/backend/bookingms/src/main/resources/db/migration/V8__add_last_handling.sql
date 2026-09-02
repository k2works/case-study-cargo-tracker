-- 最後に荷役があった地点と日時（US30・[ADR-025] 決定 4）。
--
-- **陸揚げ地の候補「現在地の港」はこれを使う。** trackingms へ引かない——現在地の
-- 一次情報は荷役にあり、trackingms もそれを購読して得ている。2 ホップ先から
-- 取りに行くと、同じ事実の伝聞が 1 段増える。
--
-- **NULL を許す。** まだ荷役が起きていない予約がある。列が無かったころの行も読める
-- ようにする（不変条件の追加は既存行を壊す）。
ALTER TABLE cargo ADD COLUMN last_handling_location_unlocode VARCHAR(5)
    REFERENCES location (unlocode);
ALTER TABLE cargo ADD COLUMN last_handling_at TIMESTAMP WITH TIME ZONE;
