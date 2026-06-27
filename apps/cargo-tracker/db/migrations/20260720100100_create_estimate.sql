-- migrate:up

-- IT2 US01 輸送見積集約
-- shipper への参照は業務キーではなくサロゲートキー (shipper.id) を参照する
-- (data-model.md PK 規約準拠)。estimate_id は UUID で集約識別子。

CREATE TABLE estimate (
    id                  BIGSERIAL PRIMARY KEY,
    estimate_id         UUID NOT NULL UNIQUE,
    shipper_id          BIGINT NOT NULL REFERENCES shipper(id),
    origin_unlocode     VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    destination_unlocode VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    deadline            TIMESTAMP WITH TIME ZONE NOT NULL,
    cargo_type          VARCHAR(20) NOT NULL,
    weight_kg           NUMERIC NOT NULL,
    estimate_status     VARCHAR(20) NOT NULL,
    version             BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT estimate_cargo_type_check
        CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED')),
    CONSTRAINT estimate_status_check
        CHECK (estimate_status IN ('Created', 'Expired')),
    CONSTRAINT estimate_weight_positive
        CHECK (weight_kg > 0)
);

CREATE INDEX estimate_shipper_id_idx ON estimate (shipper_id);
CREATE INDEX estimate_status_idx ON estimate (estimate_status);

-- migrate:down

DROP INDEX IF EXISTS estimate_status_idx;
DROP INDEX IF EXISTS estimate_shipper_id_idx;
DROP TABLE estimate;
