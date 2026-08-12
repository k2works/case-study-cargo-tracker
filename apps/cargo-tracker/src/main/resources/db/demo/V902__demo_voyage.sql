-- 動作確認用の航海スケジュール。
--
-- **ユーザーマニュアル（docs/manual/05-航路管理.md）の画面キャプチャと同じ内容にする。**
-- マニュアルの図と開発環境の画面が食い違うと、読者はどちらが正しいか判断できない。
--
-- db/demo 配下であり、demo プロファイル（local / dev）でのみ適用される。
-- 港は common/V6 の港マスタに実在するものだけを使う。
--
-- **出発日を固定日にしない。** 固定日にすると時間の経過とともに過去の便になり、
-- 「もう出港した便が検索で出てくる」という業務上あり得ない画面がマニュアルに残る。
--
-- **INTERVAL は標準の書き方（INTERVAL 'N' DAY）を使う。** PostgreSQL 独自の
-- INTERVAL 'N days' は H2 が解釈できず、ローカル起動だけが落ちる（実測）。
-- db/demo は local / dev の両方に適用されるため、両方が解釈できる SQL に限る。

-- 直行便（大阪 → ロサンゼルス。一般貨物・冷凍）
INSERT INTO voyage (
    voyage_number, vessel_name, carrier_name, cargo_types, capacity_weight_kg)
VALUES ('V0001', 'さくら丸', '日本海運', 'GENERAL,REFRIGERATED', 50000);

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'JPOSA', 'USLAX',
       CURRENT_TIMESTAMP + INTERVAL '7' DAY,
       CURRENT_TIMESTAMP + INTERVAL '21' DAY, 0
  FROM voyage WHERE voyage_number = 'V0001';

-- 乗り継ぎ便（横浜 → シンガポール → ハンブルク。一般貨物のみ）
INSERT INTO voyage (
    voyage_number, vessel_name, carrier_name, cargo_types, capacity_weight_kg)
VALUES ('V0002', 'みなと丸', 'アジア汽船', 'GENERAL', 50000);

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'JPYOK', 'SGSIN',
       CURRENT_TIMESTAMP + INTERVAL '10' DAY,
       CURRENT_TIMESTAMP + INTERVAL '17' DAY, 0
  FROM voyage WHERE voyage_number = 'V0002';

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'SGSIN', 'DEHAM',
       CURRENT_TIMESTAMP + INTERVAL '19' DAY,
       CURRENT_TIMESTAMP + INTERVAL '40' DAY, 1
  FROM voyage WHERE voyage_number = 'V0002';

-- 危険物対応便（神戸 → ロッテルダム）
INSERT INTO voyage (
    voyage_number, vessel_name, carrier_name, cargo_types, capacity_weight_kg)
VALUES ('V0003', 'ほくと丸', '欧州ライン', 'GENERAL,HAZARDOUS', 50000);

INSERT INTO carrier_movement (
    voyage_id, departure_location_unlocode, arrival_location_unlocode,
    departure_date, arrival_date, seq_number)
SELECT id, 'JPKOB', 'NLRTM',
       CURRENT_TIMESTAMP + INTERVAL '14' DAY,
       CURRENT_TIMESTAMP + INTERVAL '45' DAY, 0
  FROM voyage WHERE voyage_number = 'V0003';

-- 経路割り当て待ちの予約を 1 件用意する（V901 の予約のうち 1 件を引き渡し済みにする）。
-- **経路設計者の画面が空のままだと、キャプチャに何も写らない。**
UPDATE cargo SET booking_status = 'ROUTE_PROPOSED'
 WHERE booking_id = '33333333-3333-4333-8333-333333333333';
