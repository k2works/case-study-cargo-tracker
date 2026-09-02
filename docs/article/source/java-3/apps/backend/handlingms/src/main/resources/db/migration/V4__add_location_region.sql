-- 地点の地域区分（[ADR-027] 決定 1 の改訂・IT12）。
-- **距離の代わりである。**緯度経度は無く、全港の座標整備は本 IT の範囲を超える。
-- 区分なら 1 列で足りる。区間係数は両端の区分の重いほうを採る。
--
-- 複製である（[ADR-014]）。booking / routing / handling / tracking に同じ文を配る。
-- LocationSeedReplicaTest がずれを落とす。
ALTER TABLE location ADD COLUMN region VARCHAR(20);

-- 既存行を埋めてから NOT NULL にする。先に NOT NULL を付けると、
-- 列が無かったころの行が読めなくなる。
UPDATE location SET region = 'DOMESTIC' WHERE country_code = 'JP';
UPDATE location SET region = 'NEAR_SEA' WHERE country_code IN ('CN', 'SG');
UPDATE location SET region = 'OCEAN' WHERE region IS NULL;

ALTER TABLE location ALTER COLUMN region SET NOT NULL;
