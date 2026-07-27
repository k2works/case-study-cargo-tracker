-- Handling Context: 荷役作業記録（集約ルート）と通関申告（US15/US16・IT6）。
-- consignee_confirmation は引取（CLAIM）時の荷受人確認（署名/確認コード・US16）。
CREATE TABLE handling_activity (
    id                     BIGSERIAL PRIMARY KEY,
    booking_id             VARCHAR(20)  NOT NULL,
    event_type             VARCHAR(30)  NOT NULL,
    event_completion_time  TIMESTAMP    NOT NULL,
    location_unlocode      VARCHAR(5)   NOT NULL,
    voyage_number          VARCHAR(20),
    consignee_confirmation VARCHAR(200),
    operator_name          VARCHAR(200),
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_handling_activity_booking_id ON handling_activity (booking_id);

CREATE TABLE customs_declaration (
    id                  BIGSERIAL PRIMARY KEY,
    handling_activity_id BIGINT      NOT NULL REFERENCES handling_activity (id) ON DELETE CASCADE,
    declaration_number  VARCHAR(50)  NOT NULL UNIQUE,
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    declared_at         TIMESTAMP    NOT NULL,
    cleared_at          TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
