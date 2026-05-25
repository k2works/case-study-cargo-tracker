-- 経路設計依頼の Read Model（US06 / cross-service、ADR-0009）
-- bookingms から受信した RouteDesignRequestedEvent を記録し、経路設計者のワークベンチ（IT4）の入力とする。
CREATE TABLE IF NOT EXISTS route_design_request (
    booking_id           VARCHAR(36) NOT NULL PRIMARY KEY,
    origin_unlocode      VARCHAR(5)  NOT NULL,
    destination_unlocode VARCHAR(5)  NOT NULL,
    arrival_deadline     DATE        NOT NULL,
    cargo_type           VARCHAR(16) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
