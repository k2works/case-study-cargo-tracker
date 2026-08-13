-- 危険物申告と温度管理条件（US05 / IT9）。
--
-- **data-model.md には最初から定義されていたが、マイグレーションに無かった。**
-- 種別の CHECK（GENERAL / HAZARDOUS / REFRIGERATED）は V1 からあるのに、
-- その種別に応じた情報を書く場所が無い状態が IT1 から続いていた。
ALTER TABLE cargo ADD COLUMN hazardous_class VARCHAR(10);
ALTER TABLE cargo ADD COLUMN un_number VARCHAR(10);
ALTER TABLE cargo ADD COLUMN proper_shipping_name VARCHAR(200);

ALTER TABLE cargo ADD COLUMN min_temperature NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN max_temperature NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN temperature_unit VARCHAR(20);

-- **種別との整合は DB の CHECK で書かない。**
--
-- 「危険物なら 3 列すべてが必要、冷凍なら別の 3 列、一般ならどちらも NULL」は
-- SQL で書けなくはないが、**種別が増えるたびに条件が伸びて読めなくなる**。
-- 判断はドメイン（CargoSpecification）が持ち、ここでは書ける値の形だけを決める。
--
-- 温度の単位だけは値の集合が固定であるため CHECK を置く。
-- **列挙の綴り間違いは、種別との整合とは別の問題**である。
ALTER TABLE cargo
    ADD CONSTRAINT chk_cargo_temperature_unit
    CHECK (temperature_unit IS NULL OR temperature_unit IN ('CELSIUS', 'FAHRENHEIT'));

-- 最低 <= 最高。**これは種別に依らず常に成り立つ**ため DB でも守る。
ALTER TABLE cargo
    ADD CONSTRAINT chk_cargo_temperature_range
    CHECK (min_temperature IS NULL OR max_temperature IS NULL
           OR min_temperature <= max_temperature);
