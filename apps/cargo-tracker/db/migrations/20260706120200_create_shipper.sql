-- migrate:up

-- IT1 US02/US03 荷主集約
-- 個人 / 法人を ShipperKind 列で区別し、法人時のみ corporate_number / contract_rank を持つ
CREATE TABLE shipper (
    id                  BIGSERIAL PRIMARY KEY,
    shipper_id          VARCHAR(20) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    address             VARCHAR(500) NOT NULL,
    shipper_kind        VARCHAR(20) NOT NULL,
    corporate_number    VARCHAR(13),
    contract_rank       VARCHAR(20),
    version             BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT shipper_shipper_id_format
        CHECK (shipper_id ~ '^SHP-[A-Z0-9]{6}$'),
    CONSTRAINT shipper_kind_check
        CHECK (shipper_kind IN ('Individual', 'Corporate')),
    CONSTRAINT shipper_corporate_fields
        CHECK (
            (shipper_kind = 'Individual' AND corporate_number IS NULL AND contract_rank IS NULL)
            OR
            (shipper_kind = 'Corporate' AND corporate_number IS NOT NULL AND contract_rank IS NOT NULL)
        ),
    CONSTRAINT shipper_corporate_number_format
        CHECK (corporate_number IS NULL OR corporate_number ~ '^[0-9]{13}$'),
    CONSTRAINT shipper_contract_rank_check
        CHECK (contract_rank IS NULL OR contract_rank IN ('Bronze', 'Silver', 'Gold'))
);

CREATE INDEX shipper_email_idx ON shipper (email);

-- migrate:down

DROP TABLE shipper;
