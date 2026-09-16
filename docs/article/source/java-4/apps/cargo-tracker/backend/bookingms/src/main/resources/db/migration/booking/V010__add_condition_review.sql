-- 条件の見直し依頼（US10 §受入基準 4 / ADR-0009）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 差し戻しは状態遷移にしない（ADR-0009 決定 1）。RoutingStatus を NOT_ROUTED へ
-- 戻すと「一度も設計していない予約」と混ざり、経路設計作業一覧（S30）から消えて
-- 誰も設計を再開しない。変わったのは「誰の手番か」なので、記録で表して営業の
-- ダッシュボード（S02）に出す。
--
-- 差し戻しは何度でも起きうるが、履歴は持たない。営業が読むのは「いま見直しを
-- 頼まれているか」だけで、経緯は Event Store にある。
ALTER TABLE cargo_summary ADD COLUMN condition_review_requested_at TIMESTAMPTZ;
ALTER TABLE cargo_summary ADD COLUMN condition_review_reason VARCHAR(200);
