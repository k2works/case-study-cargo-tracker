-- Pricing BC の通貨換算レート (US21 Phase 8, IT6)
-- data-model.md §currency_rate に対応 (6.2 上流補完で追記予定の設計に整合)。
--
-- 同一 (from, to) の組でも期間ごとに複数レコードを保持する
-- (レート改定の履歴として)。有効期間の重複は Application 層で検証する
-- (トリガーで排他制約を掛ける案は将来検討)。

-- migrate:up
CREATE TABLE currency_rate (
    id                BIGSERIAL PRIMARY KEY,
    from_currency     VARCHAR(3) NOT NULL,
    to_currency       VARCHAR(3) NOT NULL,
    rate              BIGINT NOT NULL CHECK (rate >= 0),
    valid_from        TIMESTAMPTZ NOT NULL,
    valid_to          TIMESTAMPTZ NOT NULL,
    version           INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (from_currency <> to_currency),
    CHECK (valid_from < valid_to)
);
CREATE INDEX idx_currency_rate_lookup
  ON currency_rate (from_currency, to_currency, valid_from);

-- デモ用シード。
INSERT INTO currency_rate (from_currency, to_currency, rate, valid_from, valid_to)
VALUES ('USD', 'JPY', 150, '2026-01-01T00:00:00Z', '2100-01-01T00:00:00Z');
INSERT INTO currency_rate (from_currency, to_currency, rate, valid_from, valid_to)
VALUES ('EUR', 'JPY', 165, '2026-01-01T00:00:00Z', '2100-01-01T00:00:00Z');

-- migrate:down
DROP TABLE currency_rate;
