-- 航海の積載可能重量（US09。IT4 レビュー M2 / 持ち越し C1）。
--
-- IT4 では候補テーブルの「空き」列を出さなかった。**判定の材料が無く、
-- 常に「あり」を返す装置になる**ためである。経路が確定して初めて容量は減るので、
-- 確定を実装する本 IT が、初めて「満船の便は選べない」を壊して赤にできる。
--
-- 既存の航海には既定値を入れる。**NULL のままにすると「容量が分からない便」と
-- 「容量が無い便」を区別できなくなり、判定が黙って通る。**
ALTER TABLE voyage
    ADD COLUMN capacity_weight_kg NUMERIC(12, 3);

UPDATE voyage SET capacity_weight_kg = 100000 WHERE capacity_weight_kg IS NULL;

ALTER TABLE voyage ALTER COLUMN capacity_weight_kg SET NOT NULL;

ALTER TABLE voyage
    ADD CONSTRAINT chk_voyage_capacity CHECK (capacity_weight_kg > 0);
