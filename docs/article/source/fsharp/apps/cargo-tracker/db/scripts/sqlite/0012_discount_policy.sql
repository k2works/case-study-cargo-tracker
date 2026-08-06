-- discount_policy（割引ポリシーマスタ・US-ADM-01）。SQLite 方言。
-- 運用管理者が登録・変更・無効化する。有効期間内かつ active=1 のポリシーのみ US22 の割引計算に使う。
CREATE TABLE discount_policy (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    policy_type          TEXT    NOT NULL,
    discount_rate        NUMERIC NOT NULL,
    applicable_condition TEXT,
    effective_from       TEXT    NOT NULL,
    effective_to         TEXT,
    active               INTEGER NOT NULL DEFAULT 1,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL
);

CREATE INDEX idx_discount_policy_active ON discount_policy(active);
