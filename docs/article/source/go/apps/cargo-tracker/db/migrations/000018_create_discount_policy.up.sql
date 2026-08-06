-- Discount Policy Context: 管理者が管理する割引ポリシー（US-ADM-01）。
-- Billing の割引率算出（値オブジェクト）とは独立した管理コンテキストの集約。
-- 割引率は data-model.md と一貫し NUMERIC(5,4)・0.0000〜0.3000（最大 30%）で保持する。
CREATE TABLE discount_policy (
    id           UUID          PRIMARY KEY,
    policy_type  VARCHAR(30)   NOT NULL,
    discount_rate NUMERIC(5, 4) NOT NULL
                 CHECK (discount_rate BETWEEN 0.0000 AND 0.3000),
    valid_from   DATE          NOT NULL,
    valid_until  DATE,
    description  VARCHAR(200)  NOT NULL DEFAULT '',
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CHECK (valid_until IS NULL OR valid_until >= valid_from)
);

CREATE INDEX idx_discount_policy_type ON discount_policy (policy_type);
