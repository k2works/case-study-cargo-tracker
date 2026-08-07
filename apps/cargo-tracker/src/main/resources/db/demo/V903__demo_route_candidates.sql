-- 経路候補の算出（US08）を動作確認できるようにする航海スケジュール。
--
-- V902 の時点では、経路割り当て待ちの予約（JPYOK → DEHAM・冷凍貨物）に対して
-- **冷凍を運べる便が 1 つも無く、候補がすべて「選べない」状態にしかならなかった。**
-- 選べる候補・選べない候補・期限を過ぎる候補の 3 つが並んで初めて、
-- 画面が伝えようとしていることを確かめられる。
--
-- 港は common/V6 の港マスタに実在するものだけを使う。
-- INTERVAL は標準の書き方（INTERVAL 'N' DAY）を使う（H2 が解釈できる形）。

-- 選べる候補: 直行・冷凍対応・期限内（予約の期限は登録日 +45 日）
INSERT INTO voyage (
    voyage_number, vessel_name, carrier_name, cargo_types, capacity_weight_kg)
VALUES ('V0004', 'ふじ丸', '北欧ライン', 'GENERAL,REFRIGERATED', 50000);

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'JPYOK', 'DEHAM',
       CURRENT_TIMESTAMP + INTERVAL '12' DAY,
       CURRENT_TIMESTAMP + INTERVAL '38' DAY, 0
  FROM voyage WHERE voyage_number = 'V0004';

-- 期限を過ぎる候補: 冷凍対応だが到着が期限より後
INSERT INTO voyage (
    voyage_number, vessel_name, carrier_name, cargo_types, capacity_weight_kg)
VALUES ('V0005', 'あさひ丸', '北欧ライン', 'GENERAL,REFRIGERATED', 50000);

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'JPYOK', 'SGSIN',
       CURRENT_TIMESTAMP + INTERVAL '15' DAY,
       CURRENT_TIMESTAMP + INTERVAL '25' DAY, 0
  FROM voyage WHERE voyage_number = 'V0005';

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'SGSIN', 'DEHAM',
       CURRENT_TIMESTAMP + INTERVAL '27' DAY,
       CURRENT_TIMESTAMP + INTERVAL '60' DAY, 1
  FROM voyage WHERE voyage_number = 'V0005';
