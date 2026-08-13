-- 追跡番号の採番と一意性（IT6 / US14）。
--
-- 受入基準は「追跡番号は一意に採番される」と定めているが、V1 の cargo.tracking_number に
-- UNIQUE 制約が無く、**DB は重複を受け付ける状態**だった（tracking_activity 側にのみ UK がある）。
-- 追跡番号は荷主が問い合わせに使う唯一の手がかりであり、重複すると別の貨物の状態を
-- 答えることになる。
--
-- 採番はシーケンスで行う。MAX + 1 で採番すると、2 人が同時に発行したとき両者が
-- 同じ最大値を読み、片方が UNIQUE 制約で落ちる（IT1 持ち越しで荷主コードに起きた問題と同型）。
-- **既存データとの整合（setval）はここに置かない。** setval は H2 に存在せず、
-- common/ に置くとローカル起動が落ちる（ADR-003。V4 と同じ理由）。

CREATE SEQUENCE tracking_number_seq START WITH 1 INCREMENT BY 1;

-- 発行前の貨物は NULL であり、NULL は UNIQUE 制約の対象外である
-- （PostgreSQL・H2 とも複数行の NULL を許す）。**発行済みの番号だけが一意になる。**
ALTER TABLE cargo
    ADD CONSTRAINT uk_cargo_tracking_number UNIQUE (tracking_number);
