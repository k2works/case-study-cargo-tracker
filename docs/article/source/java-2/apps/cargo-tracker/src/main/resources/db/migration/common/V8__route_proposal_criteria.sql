-- 経路提案に「探索条件の貨物種別と重量」を持たせる（IT4 / US08）。
--
-- domain-model.md の RoutingCriteria は貨物種別と重量を含むが、V1 の
-- booking_route_proposal にはどちらの列も無く、**保存した提案から探索条件を
-- 復元できなかった**。IT2 の cargo、IT3 の voyage と同じ型の欠落である。
--
-- 予約から読み直す案は採らない。**探索条件は「そのとき何で探したか」であり、
-- 予約の現在値とは別の事実**である。予約側が後から変わっても、
-- 算出済みの候補がどの条件で出たものかは変わらない。

ALTER TABLE booking_route_proposal
    ADD COLUMN cargo_type VARCHAR(30);

ALTER TABLE booking_route_proposal
    ADD COLUMN weight NUMERIC(10, 3);

-- 既存行は無い（本 IT が最初の利用者である）ため、既定値の補完は不要。
-- 以後は必須とする。
UPDATE booking_route_proposal SET cargo_type = 'GENERAL' WHERE cargo_type IS NULL;
UPDATE booking_route_proposal SET weight = 1 WHERE weight IS NULL;

ALTER TABLE booking_route_proposal ALTER COLUMN cargo_type SET NOT NULL;
ALTER TABLE booking_route_proposal ALTER COLUMN weight SET NOT NULL;

ALTER TABLE booking_route_proposal
    ADD CONSTRAINT chk_proposal_cargo_type
    CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED'));

ALTER TABLE booking_route_proposal
    ADD CONSTRAINT chk_proposal_weight CHECK (weight > 0);
