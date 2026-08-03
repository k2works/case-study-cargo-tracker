-- 危険物申告・温度管理条件（IT5・US05）
-- カラム定義は docs/design/data-model.md の `cargo` 節を正典とする。
--
-- IT4 で「将来追加予定カラム」としていたものを、それを使うイテレーションで追加する
-- （docs/development/development_strategy.md「後で必要になるから今作る」は禁止）。
--
-- NOT NULL を付けない。危険物申告は貨物種別が HAZARDOUS のときだけ、
-- 温度管理条件は REFRIGERATED のときだけ値を持つ。種別と値の対応は
-- ドメイン（BookingModel.checkSpecialRequirements）が守る。
-- DB の CHECK 制約で二重に表現しないのは、**同じルールを 2 箇所に書くと
-- 片方だけ変わる**ためである（ビジネスルールの正典はドメイン）。
--
-- H2 と PostgreSQL の双方で動く標準構文だけを使う。片方でしか動かない構文を
-- 書いた時点で、もう片方の環境は検証されていないことになる（IT4 の教訓）。
ALTER TABLE cargo ADD COLUMN hazardous_class       VARCHAR(10);
ALTER TABLE cargo ADD COLUMN un_number             VARCHAR(10);
ALTER TABLE cargo ADD COLUMN proper_shipping_name  VARCHAR(200);
ALTER TABLE cargo ADD COLUMN min_temperature       NUMERIC(10,3);
ALTER TABLE cargo ADD COLUMN max_temperature       NUMERIC(10,3);
ALTER TABLE cargo ADD COLUMN temperature_unit      VARCHAR(20);
