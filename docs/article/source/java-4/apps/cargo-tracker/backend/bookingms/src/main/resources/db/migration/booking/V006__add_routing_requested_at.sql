-- 予約が経路設計者へ引き渡された日時（US06）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 経路設計作業一覧（S30）は到着期限が近い順に並ぶ。期限が遠い案件は下に沈むので、
-- 引き渡しからどれだけ経ったかが読めないと、放置されたまま誰も気づけない
-- （IT3 レビュー）。件数を出すだけでは仕事が進まないので、行から読めるようにする。
ALTER TABLE cargo_summary ADD COLUMN routing_requested_at TIMESTAMPTZ;
