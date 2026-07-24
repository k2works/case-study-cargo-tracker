-- Shipper Context: 荷主テーブル
CREATE TABLE shipper (
    id              BIGSERIAL PRIMARY KEY,
    shipper_code    VARCHAR(20)  NOT NULL UNIQUE,
    shipper_type    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL UNIQUE,
    phone           VARCHAR(50),
    address         VARCHAR(500),
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(5, 4),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
