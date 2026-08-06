-- ADR 0006: 航海データモデル追補（US07/US08 前提）
-- (a) voyage に船名・運送会社カラムを追加
-- (b) 中間テーブル voyage_supported_cargo_type を新設（多対多）

-- IT4 タスク 0.2: 船名・運送会社は必須項目として保持（DEFAULT '' は撤去）。
-- アプリ層は Voyage.register の必須引数として明示渡しを強制する。
ALTER TABLE voyage
    ADD COLUMN vessel_name  VARCHAR(100) NOT NULL,
    ADD COLUMN carrier_code VARCHAR(20)  NOT NULL;

CREATE INDEX idx_voyage_carrier_code ON voyage (carrier_code);

CREATE TABLE voyage_supported_cargo_type (
    id          BIGSERIAL PRIMARY KEY,
    voyage_id   BIGINT      NOT NULL REFERENCES voyage(id) ON DELETE CASCADE,
    cargo_type  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_voyage_supported_cargo_type UNIQUE (voyage_id, cargo_type),
    CONSTRAINT ck_voyage_supported_cargo_type CHECK (cargo_type IN ('General', 'Refrigerated', 'Hazardous'))
);

CREATE INDEX idx_voyage_supported_cargo_type_voyage ON voyage_supported_cargo_type (voyage_id);
