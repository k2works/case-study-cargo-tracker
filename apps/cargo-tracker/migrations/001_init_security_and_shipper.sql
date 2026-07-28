-- IT1 初期スキーマ: 認証基盤（Security）+ 荷主（Shipper Context）
-- 出典: docs/design/data-model.md（users / user_roles / shipper）

-- Up Migration

-- Security Context
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(200) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,  -- bcrypt ハッシュ
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,  -- 連続認証失敗回数（US26 ロック判定）
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id  BIGINT      NOT NULL REFERENCES users(id),
    role     VARCHAR(50) NOT NULL,  -- ROLE_SHIPPER / ROLE_SALES / ROLE_ROUTE_DESIGNER / ROLE_TRACKER / ROLE_HANDLER / ROLE_BILLING
    PRIMARY KEY (user_id, role)
);

-- Shipper Context
CREATE TABLE shipper (
    id              BIGSERIAL PRIMARY KEY,
    shipper_code    VARCHAR(20)  NOT NULL UNIQUE,  -- SHP-XXXXXX 形式
    shipper_type    VARCHAR(20)  NOT NULL,          -- INDIVIDUAL / CORPORATE
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    phone           VARCHAR(50),
    contract_number VARCHAR(50),                    -- 法人のみ（NULLable）
    discount_rate   NUMERIC(5,4) DEFAULT 0.0000
                    CHECK (discount_rate BETWEEN 0.0000 AND 0.3000),  -- 0.0000〜0.3000 (最大 30%)
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_shipper_email ON shipper (email);
