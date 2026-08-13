-- 予約の出発地・目的地に港マスタへの外部キーを張る（IT4 / US08）。
--
-- V1 の時点では location にデータが 1 件も無く（IT3 で投入）、外部キーを
-- 張れなかった。港マスタが揃った今、経路探索の起点・終点が
-- **マスタに無いのか、便が無いのか**を区別できるようにする。
--
-- この区別は候補ゼロの説明に効く。マスタに無い港を起点にすると、
-- 経路が見つからない理由が「便が無い」と読めてしまい、
-- 経路設計者は存在しない便を探し続けることになる。

ALTER TABLE cargo
    ADD CONSTRAINT fk_cargo_origin
    FOREIGN KEY (origin_unlocode) REFERENCES location (unlocode);

ALTER TABLE cargo
    ADD CONSTRAINT fk_cargo_destination
    FOREIGN KEY (destination_unlocode) REFERENCES location (unlocode);
