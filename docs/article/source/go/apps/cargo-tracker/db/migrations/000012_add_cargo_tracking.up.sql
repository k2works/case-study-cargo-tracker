-- Booking Context: 追跡番号発行（US14・IT6）に伴う cargo 拡張。
-- transport_status は共有カーネル TransportStatus、tracking_number は Tracking との紐付けキー。
ALTER TABLE cargo
    ADD COLUMN transport_status VARCHAR(30) NOT NULL DEFAULT 'NOT_RECEIVED',
    ADD COLUMN tracking_number  VARCHAR(20);

CREATE INDEX idx_cargo_tracking_number ON cargo (tracking_number);
