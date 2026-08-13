-- PostgreSQL 固有のインデックス（ADR-003）。
--
-- 部分インデックスは H2 が解釈できないため common/ に置けない。
-- その結果、**ローカル（H2）ではこれらのインデックスが存在しない**。
-- ローカルで一覧が速いことは本番で速いことを意味しないため、
-- 効果は Repository テスト（実 PostgreSQL）と負荷試験で確認する。

-- 未解決例外の一覧。追跡管理者が毎朝引く最重要クエリ（operation.md 業務運用）。
CREATE INDEX idx_tracking_exception_unresolved
    ON tracking_exception_event (tracking_id, occurred_at DESC)
    WHERE resolved_at IS NULL;

-- 通関で留置中の申告。3 日以上の留置は保管料が発生するため督促対象になる。
CREATE INDEX idx_customs_held
    ON customs_declaration (declared_at)
    WHERE status = 'HELD';

-- 経路未割り当ての予約。経路割り当て待ち一覧（ROLE_ROUTER の作業入口）で引く。
CREATE INDEX idx_cargo_unrouted
    ON cargo (arrival_deadline)
    WHERE routing_status = 'NOT_ROUTED';
