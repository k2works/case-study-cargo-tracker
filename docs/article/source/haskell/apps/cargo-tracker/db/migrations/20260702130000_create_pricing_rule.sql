-- Pricing BC の輸送料金ルール (US21 Phase 8, IT6)
-- data-model.md §pricing_rule に対応 (6.2 上流補完で追記予定の設計に整合)。
--
-- 1 通貨コード = 1 ルールを想定するシンプル設計 (currency に UNIQUE)。
-- 将来的にカテゴリ別ルールが必要になったら cargo_category / min_weight /
-- max_weight を追加して複合キー化する。

-- migrate:up
CREATE TABLE pricing_rule (
    id                     BIGSERIAL PRIMARY KEY,
    currency               VARCHAR(3) NOT NULL UNIQUE,
    base_rate              BIGINT NOT NULL CHECK (base_rate >= 0),
    distance_rate_per_km   BIGINT NOT NULL CHECK (distance_rate_per_km >= 0),
    weight_rate_per_kg     BIGINT NOT NULL CHECK (weight_rate_per_kg >= 0),
    version                INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- デモ用シード (Main の InMemory 実装と同じ値)。
INSERT INTO pricing_rule (currency, base_rate, distance_rate_per_km, weight_rate_per_kg)
VALUES ('JPY', 10000, 100, 50);
INSERT INTO pricing_rule (currency, base_rate, distance_rate_per_km, weight_rate_per_kg)
VALUES ('USD', 100, 1, 1);

-- migrate:down
DROP TABLE pricing_rule;
