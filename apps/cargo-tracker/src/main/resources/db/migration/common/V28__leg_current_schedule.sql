-- 区間が持つ「いまの日程」の写し（IT12 持ち越し C3 / ADR-015）。
--
-- IT11 までは予約詳細で **voyage と carrier_movement を JOIN して**いまの日程を読み、
-- 当初の日程（leg.load_time / unload_time）と突き合わせて「日程が変わりました」の印を
-- 出していた。voyage と carrier_movement は Routing の持ち物であり、
-- Booking のマッパーが触るのは BC をまたぐ結合である（MapperTableOwnershipTest の
-- 許容リストに「次に返す候補」として名前を残していた）。
--
-- **航海の更新イベントを Booking が購読して、ここに写す。** 予約が読むのは
-- 自分の BC のテーブルだけになる。
--
-- **NULL 可にする。** 本マイグレーション以前に確定した区間は写しを持たない。
-- **読み戻す側は拒まない** — 写しが無いことは「日程が変わっていない」と同じ扱いにする
-- （V22 / V23 / V24 / V26 と同じ判断。新しい不変条件で既存の行を読めなくしない）。
ALTER TABLE leg ADD COLUMN current_load_time   TIMESTAMP WITH TIME ZONE;
ALTER TABLE leg ADD COLUMN current_unload_time TIMESTAMP WITH TIME ZONE;

-- 既存の区間に、いまの日程を一度だけ写す。
-- **移行の時点で他 BC のテーブルを読むのは構わない。** 越境を禁じているのは
-- アプリケーションのマッパーであり、スキーマ移行は DB の中で完結する一度きりの操作である。
--
-- **UPDATE ... FROM を使わない。** PostgreSQL 固有の構文であり、
-- common に置くと H2 でのローカル起動だけが落ちる（本番のテストは全緑のまま）。
-- 相関サブクエリは両方が解釈できる。
UPDATE leg
   SET current_load_time = (
           SELECT m.departure_date
             FROM carrier_movement m
             JOIN voyage v ON v.id = m.voyage_id
            WHERE v.voyage_number = leg.voyage_number
              AND m.departure_location_unlocode = leg.load_location_unlocode
              AND m.arrival_location_unlocode   = leg.unload_location_unlocode
       ),
       current_unload_time = (
           SELECT m.arrival_date
             FROM carrier_movement m
             JOIN voyage v ON v.id = m.voyage_id
            WHERE v.voyage_number = leg.voyage_number
              AND m.departure_location_unlocode = leg.load_location_unlocode
              AND m.arrival_location_unlocode   = leg.unload_location_unlocode
       );
