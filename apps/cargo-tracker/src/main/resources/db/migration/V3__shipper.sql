-- shipper テーブル（荷主）
CREATE TABLE IF NOT EXISTS shipper (
    id              UUID PRIMARY KEY,
    shipper_code    VARCHAR(20)  NOT NULL,
    shipper_type    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    phone           VARCHAR(50),
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(5,4),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_shipper_code UNIQUE (shipper_code),
    CONSTRAINT uk_shipper_email UNIQUE (email),
    CONSTRAINT chk_shipper_type CHECK (shipper_type IN ('INDIVIDUAL', 'CORPORATE')),
    CONSTRAINT chk_discount_rate CHECK (
        discount_rate IS NULL OR (discount_rate >= 0 AND discount_rate <= 0.1500)
    )
);
