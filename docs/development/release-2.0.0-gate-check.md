---
title: Release 2.0.0 GA リリースゲート確認結果
date: 2026-10-25
release: 2.0.0
scope: Phase 4 完了 (IT7 + IT8 + IT9)
---

# Release 2.0.0 GA リリースゲート確認結果

Release 2.0.0 GA 本番公開 (US27) の事前ゲート確認。本書は AI Agent ドラフトで、ステージング検証 / 本番デプロイの実施結果は user 承認後に追記する。

## 概要

| 項目 | 内容 |
|------|------|
| リリースバージョン | 2.0.0 |
| 含まれる Phase | Phase 4 完了 (IT7 / IT8 / IT9) |
| 主要機能 | 例外処理 (US19/US20) + 料金算出 (US21) + 法人割引 (US22) + 精算 (US23) + CSV 取込 (US29) + 監査ログ (US30) |
| ベースリリース | 1.0.0 MVP (Phase 1-3 完了済) |
| 想定リリース日 | 2026-10-25 (IT9 完了時点) |

## ゲートチェックリスト

### コードレベル

| 項目 | 状態 | 確認 commit / 文書 |
|------|:---:|:---|
| 全ユニットテストがパス | ✅ | フルテスト 410+ 件 Green (IT9 ArchUnit ルール 6 追加で +1) |
| ArchUnit 6 ルール全 Green | ✅ | ルール 1-6 (IT9 で 6 追加) |
| pre-commit hook で sbt test 実行 | ✅ | `.husky/pre-commit` (IT9 0.4) |
| Flyway V1-V30 全適用済 | ✅ | 30 件 (V23-V30 が Phase 4 で追加) |
| ScalikeJdbcInvoiceRepositoryIT で V23-V29 列の永続化検証 | ✅ | IT9 0.7 (`38eab249`) |
| ScalikeJdbcAuditLogAdapterIT で audit_log 検証 | ✅ | IT9 US30 (`670f07ed`) |

### 業務機能

| 受入条件 | 状態 | 備考 |
|------|:---:|:---|
| US22 法人割引が自動適用される | ✅ | US28 Shipper UI で法人登録 → IT9 |
| US23 受入条件 1 (支払発行) | ✅ | IT8 |
| US23 受入条件 2 (入金確認) | ✅ | IT8 |
| US23 受入条件 3 (決済機関連携) | 🔄 | **手動 referenceCode 入力に縮小**、IT9 US29 で CSV 取込ブリッジ追加。Stripe/GMO 本連携は IT10+ |
| US23 受入条件 4 (期限超過) | ✅ | IT8 + IT9 Cron 連携 (`f953a109`) |
| US30 監査ログ 5 操作記録 | ✅ | IT9 (`7905a405`) |

### アーキテクチャ品質

| 項目 | 状態 | 備考 |
|------|:---:|:---|
| ADR 0016-0021 全承認 | ✅ | Phase 4 で 6 件起票 |
| 公開 Port パターン (BookingPublicApi, BookingPublicApi 経由依存) | ✅ | ADR 0017 + IT8 整合修正 (`6fe0b22c`) |
| MailNotificationPort 出力 Port | 🔄 | LoggingAdapter のみ、Pekko Mail/SES 連携は IT10 申し送り |
| HandlingOrchestrator 単一 TX 化 | 🔄 | **Phase 1 のみ** (Handling 部分)、Tracking/Booking 統合は IT10 |
| 監査ログ不変記録 (audit_log) | ✅ | UPDATE/DELETE 未提供 |

### ドキュメント

| 項目 | 状態 |
|------|:---:|
| CHANGELOG.md `[2.0.0]` 確定 | ✅ |
| ADR 0016-0021 公開 (docs/adr/) | ✅ |
| README プロジェクト進捗 | ✅ (Phase 1-4 反映済) |
| 設計ドキュメント (data-model / domain-model / ui_design) 反映 | ✅ (IT8 0.12) |
| マルチパースペクティブ実装レビュー (IT8) | ✅ |

### 運用基盤

| 項目 | 状態 | 備考 |
|------|:---:|:---|
| 期限超過検出 (detectOverdue) Cron | ✅ | Pekko Scheduler / 02:00 JST 日次 (IT9 0.3) |
| 監査ログ閲覧 (/admin/audit-logs) | ✅ | MasterAdmin 限定 (IT9 US30) |
| 入金消込 CSV 取込 | ✅ | Settlement/MasterAdmin (IT9 US29) |
| 実メール送信 (PaymentRequested / Confirmed / OverdueAlert) | 🔄 | LoggingAdapter のみ、IT10 で Pekko Mail/SES |

## ステージング検証 (TBD - user 実施)

- [ ] ステージング環境デプロイ実施 (`/ops/deploy.sh staging`)
- [ ] 全 US Smoke テスト (US01-US23 + US27-US30) PASS
- [ ] Playwright E2E (IT8 までの既存 + IT9 新規) PASS
- [ ] DB マイグレーション V23-V30 適用確認
- [ ] パフォーマンス測定: P95 < 3 秒 (経路探索 / 請求書一覧 / 監査ログ一覧)
- [ ] セキュリティスキャン (OWASP ZAP / SonarQube) クリア

## 本番デプロイ (TBD - user 実施)

- [ ] 本番環境デプロイ手順書のレビュー完了
- [ ] ロールバック手順検証 (Blue/Green デプロイ前提)
- [ ] 本番デプロイ実施
- [ ] GitHub Release v2.0.0 タグ + リリースノート公開
- [ ] CHANGELOG.md の `[2.0.0]` を「2026-10-25」(実日付) で確定

## リリース後監視 (TBD - 初日)

- [ ] CloudWatch / Sentry アラート設定 (P0 / P1 通知)
- [ ] 初日 24 時間の重大アラート 0 件
- [ ] detectOverdue Cron が翌 02:00 JST に起動成功
- [ ] CSV 取込 UI / 監査ログ UI のアクセス率測定

## 既知の制約 (IT10 申し送り)

| # | 内容 | 影響 |
|---|------|------|
| 1 | 実メール送信未実装 (LoggingAdapter のみ) | 荷主向け実通知は IT10 まで延期 (代替: 通知ログは記録) |
| 2 | HandlingOrchestrator 単一 TX 化 Phase 2 未完 | ステップ 2 失敗時の Cargo/Invoice 不整合リスク残存 (低確率) |
| 3 | Playwright E2E は IT9 範囲外 | E2E カバレッジは IT8 までの 36 件のみ、IT10 で 4 件追加予定 |
| 4 | 決済機関連携 (Stripe/GMO) は IT10+ | US23 受入条件 3 縮小、CSV 取込で代替 |
| 5 | Lost 通知連携 Controller IT 未追加 | TrackingController → BookingCommandService.escalateLost の経路は単体テストのみ |

## 関連ドキュメント

- [IT7 計画](./iteration_plan-7.md) / [IT8 計画](./iteration_plan-8.md) / [IT9 計画](./iteration_plan-9.md)
- [IT8 完了報告書](./iteration_report-8.md) / [IT8 ふりかえり](./retrospective-8.md)
- [IT8 マルチパースペクティブ実装レビュー](../review/it8_implementation_review_20260624.md)
- [CHANGELOG `[2.0.0]`](../../apps/cargo-tracker/CHANGELOG.md)
- ADR [0016](../adr/0016-handling-orchestrator-transaction-boundary.md) / [0017](../adr/0017-booking-public-api-port.md) / [0018](../adr/0018-mail-notification-port.md) / [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) / [0020](../adr/0020-public-tracking-exception-display.md) / [0021](../adr/0021-port-pattern-convention.md)
