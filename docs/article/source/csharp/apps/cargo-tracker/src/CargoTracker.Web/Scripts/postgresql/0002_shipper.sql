-- 荷主テーブル（PostgreSQL 方言・US02/US03・data-model）
-- 個人/法人を shipper_type で判別する単一テーブル。法人のみ契約番号・割引率を保持。
-- email の一意性はアプリケーション層で担保する（domain-model 規則 2）。
CREATE TABLE shipper (
    id              BIGSERIAL    PRIMARY KEY,
    shipper_code    VARCHAR(20)  NOT NULL,
    shipper_type    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    phone           VARCHAR(50),
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(5,4) NOT NULL DEFAULT 0.0000,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipper_code UNIQUE (shipper_code)
);
