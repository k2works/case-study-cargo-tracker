-- 誤配を検知した荷役の写し（US28 / IT12 の C28）。
--
-- IT11 は予約詳細でこの 2 つを **handling_activity を JOIN して**読んでいた。
-- ArchUnit も JIG も Java の依存しか見ないため、**BC をまたぐ SQL は
-- どの検査にも映らなかった**（IT11 レビュー C28）。
--
-- 荷役の登録は既に HandlingActivityRegisteredEvent で場所と日時を運んでいる。
-- **運ばれてきた事実を Booking が自分の表に写す**（ADR-009 の結果整合）。
-- こうすると Handling のテーブルを変えても Booking の SQL は壊れない。
--
-- **NULL 可にする。** 誤配でない貨物は持たないし、この列が無かったころの
-- 誤配にも値が無い。読み戻す側は NULL を拒んではならない
-- （V22 / V23 / V24 と同じ判断）。
ALTER TABLE cargo ADD COLUMN misrouted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE cargo ADD COLUMN misrouted_location_unlocode VARCHAR(5);
ALTER TABLE cargo ADD CONSTRAINT fk_cargo_misrouted_location
    FOREIGN KEY (misrouted_location_unlocode) REFERENCES location (unlocode);
