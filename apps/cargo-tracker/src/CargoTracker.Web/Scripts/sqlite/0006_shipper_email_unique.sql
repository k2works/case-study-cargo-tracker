-- 荷主メールアドレスの DB 一意制約（M3）。
-- SQLite は ALTER TABLE ADD CONSTRAINT 非対応のため UNIQUE INDEX で同等制約を表現する。
CREATE UNIQUE INDEX uk_shipper_email ON shipper(email);
