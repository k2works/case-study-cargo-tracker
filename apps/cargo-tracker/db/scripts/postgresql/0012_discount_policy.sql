-- discount_policy（割引ポリシーマスタ・US-ADM-01）。PostgreSQL 方言。
-- 運用管理者が登録・変更・無効化する。有効期間内かつ active=true のポリシーのみ US22 の割引計算に使う。
CREATE TABLE discount_policy (
    id                   BIGSERIAL   PRIMARY KEY,
    policy_type          VARCHAR(30) NOT NULL,
    discount_rate        NUMERIC(5,4) NOT NULL,
    applicable_condition VARCHAR(200),
    effective_from       DATE        NOT NULL,
    effective_to         DATE,
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discount_policy_active ON discount_policy(active);
