-- 荷主メールアドレスの DB 一意制約（M3）。
-- アプリケーション層の事前チェックに加え、DB 制約で TOCTOU 競合を最終防御する。
ALTER TABLE shipper
    ADD CONSTRAINT uk_shipper_email UNIQUE (email);
