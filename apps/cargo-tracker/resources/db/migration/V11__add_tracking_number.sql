-- 追跡番号の発行（US14・IT10）
--
-- `tracking_activity` は IT1 で作成済みで、公開追跡（US03）が読み取りに使っている。
-- 本マイグレーションで足すのは **Booking 側の記録**だけである。
--
-- `cargo.tracking_number` は NULL 許容とする。発行は予約確定（CONFIRMED）の後に
-- 起きる操作であり、**予約の登録時点では存在しない**。NOT NULL にすると
-- 「まだ発行していない」を表す値を発明することになる。
--
-- **UNIQUE を張る**。追跡番号は荷主が問い合わせに使う唯一の手掛かりであり、
-- 重複すると別の貨物の状況が見える。`tracking_activity.tracking_number` にも
-- UNIQUE があるが（V1）、Booking 側にも張るのは**採番の競合を DB で止める**ためである
-- （ADR-0012 の「1 トランザクションで 2 集約を更新する」を引き受けた帰結）。
ALTER TABLE cargo ADD COLUMN tracking_number VARCHAR(20);

ALTER TABLE cargo ADD CONSTRAINT uk_cargo_tracking_number UNIQUE (tracking_number);
