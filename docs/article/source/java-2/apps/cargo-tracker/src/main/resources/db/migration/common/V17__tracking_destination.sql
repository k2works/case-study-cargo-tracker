-- 追跡レコードに目的地と推定到着日を持たせる（ADR-012 / IT8）。
--
-- **Tracking → Booking の問い合わせ（CargoArrivalEstimates）を廃止するための列である。**
-- 廃止すると Booking ⇄ Tracking のパッケージ循環が消える。
--
-- 値は追跡番号の発行時に Booking から渡される（発行の呼び出しは既に
-- Booking → Tracking の向きであり、逆向きのポートを足す必要が無い）。
-- 経路が変わったときは CargoRoutedEvent の購読で更新する。
-- **片方だけ入れると古い値が更新されないまま残る。**
--
-- 結果整合の写しであることを承知で持つ（ADR-009 の代償と同じ性質）。
ALTER TABLE tracking_activity
    ADD COLUMN destination_unlocode VARCHAR(5) REFERENCES location (unlocode);

ALTER TABLE tracking_activity
    ADD COLUMN estimated_arrival_date DATE;

-- 既存行に値を入れる。**「発行時に渡す」だけを実装すると、
-- 既に発行済みの追跡番号は目的地が空のまま残り、照会画面から目的地が消える。**
UPDATE tracking_activity t
   SET destination_unlocode = c.destination_unlocode
  FROM cargo c
 WHERE c.booking_id = t.booking_id
   AND t.destination_unlocode IS NULL;

-- 推定到着日は確定した旅程の最終区間の荷降予定日である。
-- 旅程が無い（経路未確定の）追跡は空のままでよい。
UPDATE tracking_activity t
   SET estimated_arrival_date = latest.unload_date
  FROM (SELECT c.booking_id, MAX(CAST(l.unload_time AS DATE)) AS unload_date
          FROM cargo c
          JOIN leg l ON l.cargo_id = c.id
         GROUP BY c.booking_id) latest
 WHERE latest.booking_id = t.booking_id
   AND t.estimated_arrival_date IS NULL;
