-- T4 (ADR-0008 / IT3): 一覧のデフォルトソート（ORDER BY created_at DESC）の性能改善。
-- LIMIT/OFFSET ページネーションで created_at の降順走査を高速化する。
CREATE INDEX IF NOT EXISTS idx_cargo_summary_created_at
    ON cargo_summary (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_quotation_created_at
    ON quotation (created_at DESC);
