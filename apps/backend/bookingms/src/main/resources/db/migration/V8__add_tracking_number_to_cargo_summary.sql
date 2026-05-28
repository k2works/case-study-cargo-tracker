-- 追跡情報割当（US14 / IT5 1.4）。採番された追跡番号を保持する。
-- bookingStatus が CONFIRMED → TRACKING_ISSUED に遷移する CargoTrackingAssignedEvent の
-- 投影更新で本列がセットされる。data-model.md cargo_summary の tracking_number UNIQUE と
-- 整合する（重複採番は採番側 + UNIQUE 制約で防止）。
ALTER TABLE cargo_summary
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(25);

-- 一意性の保証（NULL は複数許容、TRK- + 10 桁の重複のみ拒否）
-- H2 と PostgreSQL 両対応のため、UNIQUE 制約ではなく UNIQUE インデックスで実装
CREATE UNIQUE INDEX IF NOT EXISTS uk_cargo_summary_tracking_number
    ON cargo_summary (tracking_number);
