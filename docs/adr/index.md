# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-haskell-servant-stack.md) | Haskell 版バックエンドスタックとして Servant + Warp を採用 | 承認 (2026-06-26) |
| [0002](0002-arch-check-implementation.md) | アーキテクチャ規約検査ツール `arch-check` の自作実装 (HLint + 自作 AST 解析のハイブリッド) | 承認 (2026-06-26) |
| [0004](0004-cross-bc-shipper-ref.md) | Cross-BC 参照に `ShipperRef` VO を導入 (ALLOWLIST 解消 / IT3 U-05 Phase 1 完了) | 採用 (2026-06-29) |
| [0005](0005-bounded-context-error-types.md) | BC 固有エラーを `Cargotracker.<BC>.Domain.Error` に分離 (Phase 1 IT3 起票) | 提案 (2026-06-29) |
| [0006](0006-pagination-strategy.md) | 一覧 Repository ページネーション戦略 (IT4 Phase 1 実装着手予定) | 採用 (2026-06-29) |
| [0007](0007-cancellation-fee-policy.md) | キャンセル料 3 段階ルールのドメインポリシー化 (Free/Partial/Full) | 採用 (2026-06-30) |
| [0008](0008-itinerary-leg-model.md) | Itinerary + Leg を Booking 集約配下に配置、ItineraryRepository ポート分離 | 採用 (2026-07-01) |
| [0009](0009-booking-state-machine.md) | Booking 状態機械の SSoT 化 (BookingStatus.canTransitionTo) | 採用 (2026-06-30) |
| [0010](0010-session-cookie-auth.md) | セッション認証方式 (opaque Cookie + Servant Auth + Postgres KV) | 提案 (2026-07-01) |
| [0011](0011-offline-handling-queue.md) | 荷役オフライン対応方式 (Service Worker + IndexedDB キュー + BackgroundSync) | 提案 (2026-07-01) |
| [0012](0012-transaction-boundary-and-cross-bc-policy.md) | トランザクション境界と Cross-BC 参照ポリシー (Single Tx / Text-DTO / 副作用外出し) | 採用 (2026-07-02) |
| [0013](0013-notification-primary-key-design.md) | Notification updateNotification 主キーを notification_id (UUID) サロゲートに移行 | 提案 (2026-07-03) |
| [0014](0014-exception-state-transition-policy.md) | 例外処理の状態遷移ポリシー (単一 Tx / 遷移マトリクス / Cargo.status 波及は US23 IT8 対応) | 提案 (2026-07-03) |
| [0015](0015-corporate-discount-rank-derived.md) | 法人契約割引率を contract_rank 由来で決定 (shipper.discount_rate カラム追加を回避) | 採用 (2026-07-03) |
| [0016](0016-role-based-authorization-design.md) | Role ベース認可の Domain/Interfaces 分離 (RolePolicy 純粋述語 + RoleGate 統合ヘルパー) | 採用 (2026-07-07) |

ADR の作成には `creating-adr` スキルを使用してください。
