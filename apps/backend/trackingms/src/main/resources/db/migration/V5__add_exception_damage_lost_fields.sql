-- tracking_exception_event テーブルに破損・紛失固有フィールドを追加
ALTER TABLE tracking_exception_event ADD COLUMN damage_description TEXT;
ALTER TABLE tracking_exception_event ADD COLUMN photo_url VARCHAR(512);
ALTER TABLE tracking_exception_event ADD COLUMN last_known_location VARCHAR(100);
ALTER TABLE tracking_exception_event ADD COLUMN last_seen_at TIMESTAMP;
