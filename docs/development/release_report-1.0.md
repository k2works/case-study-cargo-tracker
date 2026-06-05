# Release 1.0 完了報告書（暫定）

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| **プロジェクト名** | 国際貨物輸送管理システム（take-5） |
| **リリース** | Release 1.0（候補確立） |
| **期間** | IT1（2026-05-21）〜 IT8（2026-09-09）≒ 16 週間（実績 Ralph Loop で大幅短縮） |
| **状態** | 候補確立（IT9 で Stripe webhook + AWS Secrets Manager 統合により正式版に昇格予定） |

### 含まれる Phase / イテレーション

| Phase | 内容 | SP | 状態 |
|-------|------|----|------|
| Phase 1 | 基盤・認証・予約・経路設計（IT1-IT4） | 41 | ✅ |
| Phase 2 | 追跡・例外処理・精算（IT5-IT7） | 27 | ✅ |
| Phase 2 Buffer | 本番デプロイ準備（IT8） | 8 | ✅ |
| **合計** | | **76** | **100%** |

## 達成内容

### コアビジネス機能（US01-US23）

| ユーザーストーリー範囲 | 完了 |
|----------------------|------|
| US01-US05 認証・荷主管理 | ✅ |
| US06-US10 予約・経路設計 | ✅ |
| US11-US13 経路再設計・予約状態管理 | ✅ |
| US14-US17 追跡・例外処理 | ✅ |
| US18-US20 公開照会・例外救済 | ✅ |
| US21-US23 請求・割引・精算・入金・督促 | ✅ |

### アーキテクチャ・基盤達成

| 領域 | 達成 |
|------|------|
| **CQRS / Event Sourcing** | Axon Framework 5 + Saga 完全統合（8 集約） |
| **ADR-0012 集約発火型** | 二段イベント全廃（IT8 T1.10 で trackingms / handlingms 最終移行） |
| **Onion Architecture / DIP** | 全 ms で hard assertion（ArchUnit、IT8 T1.11 で handlingms 解消） |
| **ADR-0014/0016 @ProcessingGroup 命名規約** | cross-/local-/outbound- prefix 統一、token 移行完了（IT8 T1.2） |
| **Kafka cross-service** | Aiven Managed Kafka + Axon Kafka Extension（IT5-IT7） |
| **Spring Security 平準化** | 全 7 backend ms に SecurityFilterChain 導入（IT8 T1.4/T1.5） |
| **Resilience4j Circuit Breaker** | shipperInfo（半開 3 / 失敗率 50% / 30s OPEN）+ Caffeine cache TTL 5min（IT8 A3） |
| **ShedLock クラスタ排他** | OverdueScheduler @SchedulerLock + JDBC（IT8 A1） |
| **SendGrid Dynamic Templates** | trackingms 6 種 + billingms 3 種の通知メール、@ConditionalOnProperty 切替（IT8 A2） |
| **PaymentDetailRecorded 補完 event** | shared 最小契約 + 内部運用情報の分離（IT8 A4） |
| **BFS 多段経由探索** | OptimalRouteService 最大 3 段 + 循環抑止（IT8 T1.7） |
| **四半期ローテーション基盤** | TrackingTokenSecretProvider + previous-secret 互換検証（IT8 T1.6） |
| **設定駆動運用** | RateTable + paymentDueDaysByType の application.yml 化（IT8 T1.8/T1.9） |

### 品質指標

| 項目 | 値 |
|------|-----|
| **全 modules check** | ✅ PASS（authms / bookingms / routingms / trackingms / handlingms / billingms / gatewayms / shared） |
| **Frontend vitest** | ✅ 234 件 PASS |
| **ArchUnit hard assertion** | ✅ 全 ms 適用、除外項目 0 |
| **SonarQube** | Backend カバレッジ 80%+ / Frontend カバレッジ 78%+（IT5 / IT6 / IT7 で達成済） |
| **E2E** | cross-service.spec.ts（US21/US22/US23 完全フロー + PaymentDetailRecorded 補完反映検証） |

### ADR 起票・適用

| ADR | 内容 | 状態 |
|-----|------|------|
| 0001-0011 | 基盤（Axon Kafka / MyBatis / Heroku / Flyway / Pagination / cross-service Saga 等）| 承認済 / 適用済 |
| 0012 | cross-service idempotency + 集約発火型 | ✅ 完全達成（IT8 T1.10） |
| 0013 | 公開トークン JWT | ✅ 適用済（IT6 + IT8 T1.5/T1.6） |
| 0014 / 0016 | @ProcessingGroup 命名規約 + 一斉改名 | ✅ 完全適用（IT8 T1.2） |
| 0015 | billingms cross-service + ShipperInfo ACL | ✅ 完全実装（IT8 A3） |
| 0017 | OverdueScheduler ShedLock | ✅ 実装済（IT8 A1） |
| 0018 | 通知 adapter（SendGrid） | ✅ 実装済（IT8 A2） |
| 0019 | PaymentDetailRecorded 補完 event | ✅ 実装済（IT8 A4） |
| 0020 | 決済機関 webhook（Stripe） | 起票済（IT9 実装） |
| 0021 | AWS Secrets Manager + Lambda 自動回転 | 起票済（IT9 実装） |

## IT9 で完了予定（Release 1.0 正式版）

| 項目 | 内容 | SP |
|------|------|-----|
| A1 Stripe webhook | ADR-0020 実装（部分入金 PARTIALLY_PAID） | 3 |
| A2 AWS Secrets Manager | ADR-0021 実装（Lambda 90 日回転） | 2 |
| A3 認可付与 | 全 ms endpoint authenticated() + @PreAuthorize | 2 |
| A4 IT8 H1/H3 解消 | SendGrid Client 注入 WireMock + @SpringBootTest CI コスト測定 | 1 |
| **IT9 合計** | | **8** |

IT9 完了後、Release 1.0 を正式版として GitHub Release タグ + CHANGELOG 確定 + 本番デプロイ可能状態と宣言予定。

## イテレーション別の進捗

| イテレーション | 計画 SP | 実績 SP | 達成率 | 報告書 |
|---------------|---------|---------|--------|--------|
| IT1 | 10 | 10 | 100% | [iteration_report-1.md](iteration_report-1.md) |
| IT2 | 10 | 10 | 100% | [iteration_report-2.md](iteration_report-2.md) |
| IT3 | 10 | 10 | 100% | [iteration_report-3.md](iteration_report-3.md) |
| IT4 | 11 | 11 | 100% | [iteration_report-4.md](iteration_report-4.md) |
| IT5 | 10 | 10 | 100% | [iteration_report-5.md](iteration_report-5.md) |
| IT6 | 9 | 9 | 100% | [iteration_report-6.md](iteration_report-6.md) |
| IT7 | 8 | 8 | 100% | [iteration_report-7.md](iteration_report-7.md) |
| IT8 | 8 | 8 + H2 持ち越し 8 件（14h 超過達成）| 100%+ | [iteration_report-8.md](iteration_report-8.md) |
| **合計** | **76** | **76** | **100%** | |

## Release 1.0 のハイライト

### 業務面

- **8 つの境界づけられたコンテキスト**: 認証 / 予約 / 経路設計 / 追跡 / 荷役 / 請求 + Gateway + 共有カーネル
- **cross-service 完全結合**: 予約 → 経路設計 → 追跡開始 → 荷役 → 配送完了 → 請求 → 入金 → 予約完了の 8 ホップ E2E が機能
- **業務 UI 23 画面**: S01-S25 を React + htmx-style PRG パターンで実装

### 技術面

- **Axon Framework 5 + Saga**: cargo-events トピックを 7 ms で共有
- **集約発火型徹底**: 二段イベント全廃により ADR-0012 完全自己整合
- **Resilience4j fallback UI**: Circuit Breaker OPEN 時の手動入力フォーム（S23、IT8 T4.2）
- **Caffeine cache + Spring AOP**: shipperInfo TTL 5min で bookingms 同期呼出の負荷低減
- **SendGrid Dynamic Templates**: 9 種の通知テンプレートを application.yml で切替

### 運用面

- **Heroku Container Registry デプロイ**: ops/scripts/heroku.js で全 8 app の一括プロビジョニング
- **Aiven Managed Kafka**: SSL + 3 証明書管理（ca / service.cert / service.key）
- **SendGrid Add-on**: trackingms / billingms に sendgrid:starter 自動投入（IT8 T3.3）
- **ShedLock JDBC**: PostgreSQL ベースの分散ロック、複数 dyno で 1 instance のみが処理

## 関連ドキュメント

- [release_plan.md](release_plan.md) — リリース計画 + バーンダウン
- [iteration_report-8.md](iteration_report-8.md) — IT8 完了報告書（H2 持ち越し含む詳細）
- [IT8 開発成果物レビュー](../review/IT8_review_20260605.md) — マルチパースペクティブレビュー
- [docs/adr/index.md](../adr/index.md) — 全 ADR 一覧

## 更新履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-06-05 | Release 1.0 候補確立報告書（暫定版）作成。IT8 完全達成（主スコープ 8/8 SP + H2 持ち越し 8/8 件）を受けて、累計 76/76 SP を達成。Stripe webhook + AWS Secrets Manager 統合は IT9 持ち越し | k2works |
