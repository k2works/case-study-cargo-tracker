-- Booking Context: 確定経路の荷主通知の送信記録（US12）。
-- 荷主参照は BC 独立性のため業務識別子 shipper_code（文字列）で保持する。
CREATE TABLE notification (
    id            BIGSERIAL PRIMARY KEY,
    cargo_id      BIGINT       NOT NULL REFERENCES cargo (id) ON DELETE CASCADE,
    shipper_code  VARCHAR(20)  NOT NULL,
    summary       VARCHAR(500) NOT NULL,
    sent_at       TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_cargo_id ON notification (cargo_id);
