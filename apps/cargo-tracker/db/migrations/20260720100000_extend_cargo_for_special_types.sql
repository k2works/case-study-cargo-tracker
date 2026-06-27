-- migrate:up

-- IT2 US04+US05: 危険物・冷凍貨物の追加情報をサポートするため cargo テーブルを拡張する。
-- iteration_plan-2.md §データモデル変更 (論理順序 007) 参照。

ALTER TABLE cargo
    ADD COLUMN cargo_type           VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN hazardous_class      VARCHAR(10),
    ADD COLUMN un_number            VARCHAR(10),
    ADD COLUMN proper_shipping_name TEXT,
    ADD COLUMN min_temperature      NUMERIC,
    ADD COLUMN max_temperature      NUMERIC,
    ADD COLUMN temperature_unit     VARCHAR(1);

ALTER TABLE cargo
    ADD CONSTRAINT cargo_type_check
        CHECK (cargo_type IN ('GENERAL', 'HAZARDOUS', 'REFRIGERATED'));

ALTER TABLE cargo
    ADD CONSTRAINT cargo_hazardous_fields
        CHECK (
            (cargo_type = 'HAZARDOUS'
                AND hazardous_class IS NOT NULL
                AND un_number IS NOT NULL
                AND proper_shipping_name IS NOT NULL)
            OR
            cargo_type <> 'HAZARDOUS'
        );

ALTER TABLE cargo
    ADD CONSTRAINT cargo_refrigerated_fields
        CHECK (
            (cargo_type = 'REFRIGERATED'
                AND min_temperature IS NOT NULL
                AND max_temperature IS NOT NULL
                AND temperature_unit IS NOT NULL
                AND temperature_unit IN ('C', 'F'))
            OR
            cargo_type <> 'REFRIGERATED'
        );

CREATE INDEX cargo_cargo_type_idx ON cargo (cargo_type);

-- migrate:down

DROP INDEX IF EXISTS cargo_cargo_type_idx;
ALTER TABLE cargo DROP CONSTRAINT IF EXISTS cargo_refrigerated_fields;
ALTER TABLE cargo DROP CONSTRAINT IF EXISTS cargo_hazardous_fields;
ALTER TABLE cargo DROP CONSTRAINT IF EXISTS cargo_type_check;
ALTER TABLE cargo
    DROP COLUMN IF EXISTS temperature_unit,
    DROP COLUMN IF EXISTS max_temperature,
    DROP COLUMN IF EXISTS min_temperature,
    DROP COLUMN IF EXISTS proper_shipping_name,
    DROP COLUMN IF EXISTS un_number,
    DROP COLUMN IF EXISTS hazardous_class,
    DROP COLUMN IF EXISTS cargo_type;
