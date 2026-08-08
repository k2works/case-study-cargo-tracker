-- 1 つの予約に追跡レコードは 1 件だけ（IT6 レビュー H3）。
--
-- `tracking_activity.booking_id` にはインデックスしか無く、**同じ予約に 2 件目を
-- 作れる状態**だった。発行が途中で失敗して巻き戻らなかった場合や、二重に押された
-- 場合に 2 行目ができ、`findByBookingId`（単一結果を返す宣言）が
-- TooManyResultsException になる。**以後その予約は追跡情報を読めなくなる。**
--
-- 巻き戻しはアプリ側で直したが、**DB でも止める。** 追跡レコードが予約に対して
-- 1 件であることは業務上の事実であり、アプリの実装に依存させない。

ALTER TABLE tracking_activity
    ADD CONSTRAINT uk_tracking_activity_booking UNIQUE (booking_id);
