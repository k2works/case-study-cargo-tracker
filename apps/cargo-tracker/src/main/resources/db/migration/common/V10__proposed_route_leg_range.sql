-- 経路候補に「航海のどの区間に乗り、どの区間で降りるか」を持たせる（IT6 / レビュー L1）。
--
-- 確定するとき、旅程にする区間を**乗船時刻から下船時刻までの範囲**で絞っていた。
-- これは探索が決めた位置を時刻から逆算する形であり、**同じ港を 2 度通る航海では
-- どの周回の区間なのかが時刻に委ねられる**。探索が選んだ位置をそのまま保存すれば
-- 逆算は要らない。
--
-- 既存行の補完は不要である（候補は再算出のたびに全削除・再作成されるため、
-- 意味のある値を持つ行が残らない。ビジネスルール 5）。ただし NOT NULL を課す以上、
-- **既存行があっても落ちない順序**で入れる（既定値 → NOT NULL）。

ALTER TABLE proposed_route
    ADD COLUMN boarding_index INTEGER;

ALTER TABLE proposed_route
    ADD COLUMN landing_index INTEGER;

UPDATE proposed_route SET boarding_index = 0 WHERE boarding_index IS NULL;
UPDATE proposed_route SET landing_index = 0 WHERE landing_index IS NULL;

ALTER TABLE proposed_route ALTER COLUMN boarding_index SET NOT NULL;
ALTER TABLE proposed_route ALTER COLUMN landing_index SET NOT NULL;

-- 降りる区間は乗る区間より後（同じ区間で乗り降りする＝直行はあり得る）。
-- **行の中で完結する条件であるため DB で守れる**（旅程の連結制約とは異なる）。
ALTER TABLE proposed_route
    ADD CONSTRAINT chk_proposed_route_leg_range
    CHECK (boarding_index >= 0 AND landing_index >= boarding_index);
