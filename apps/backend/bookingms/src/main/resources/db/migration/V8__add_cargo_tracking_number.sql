-- Booking Microservice: cargo テーブルに tracking_number カラム追加
ALTER TABLE cargo
    ADD COLUMN tracking_number VARCHAR(50);
