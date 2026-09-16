-- 読む側の無い索引を落とす（IT13 のレビュー 中）。
--
-- V004 は `billing_cargo_snapshot` に予約からの索引を置いたが、**引く経路が
-- 無かった**——連鎖は追跡番号で引く（`CargoDeliveredEvent` が運ぶのは追跡番号と
-- 予約 ID の両方だが、写しの主キーは追跡番号）。読む側の無い索引は、次に読む人に
-- 「予約から引く経路がある」と誤解させる（IT12 の「常に 0 の列」と同じ形）。
--
-- V004 は適用済みなので編集できない。落とすのは新しい版で行う。
DROP INDEX IF EXISTS idx_billing_cargo_snapshot_booking;
