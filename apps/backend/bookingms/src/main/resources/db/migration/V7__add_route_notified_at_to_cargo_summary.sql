-- 確定経路の荷主通知送信記録（US12）。通知日時を保持する。
ALTER TABLE cargo_summary
    ADD COLUMN IF NOT EXISTS route_notified_at TIMESTAMP;
