---
title: イテレーション 8 完了報告書
date: 2026-06-24
---

# イテレーション 8 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT8 |
| 期間 | 2026-09-28 〜 2026-10-11（計画）/ 1 日（AI ペアプロ実績、Ralph Loop 自律実行）|
| ゴール | US22 法人割引 + US23 精算（計 9 SP）を完成、Release 2.0 GA に到達、IT7 申し送り（Try 8 件 + Review 高優先 12 件）を 15 件 (H1-H12 + T3 / T6 + ADR 0019/0020) 全消化 |
| 計画 SP | 9（US22: 3 + US23: 6）|
| 実績 SP | 9 |
| 達成率 | 100% |
| 連続コミット数 | 28（096c3be6 〜 907c6663）|

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US22 | 法人割引を適用する | ✅ 完了 (E2E は IT9 申し送り) | 3 | 3 |
| US23 | 精算を処理する | ✅ 完了 (受入条件 3 縮小 / E2E は IT9 申し送り) | 6 | 6 |
| **合計** | | | **9** | **9** |

## タスク実績

機能タスク 16 件 + IT7 申し送り 15 件 = **31 タスク完了**。

### IT7 申し送り（0.x、15/15 全消化）

| # | タスク | 完了内容 | 関連 commit |
|---|--------|---------|------------|
| 0.1 | withOptimisticLock ヘルパ抽出 (H1) | `shared.application.OptimisticLockOps.withOptimisticLock` 新設、TrackingCommandService 3 箇所置換、Unit 5 件 | `a915dc96` |
| 0.2 | Lost/Loss 命名統一 (H2) | `LossEscalated` → `LostEscalated` / `escalateLoss` → `escalateLost` 統一、Flyway V26、ユビキタス言語注記追加 | `5686ba88` |
| 0.3 | BookingPublicApi + ADR 0017 (H3) | 公開 Port trait 新設、`BookingCommandService extends BookingPublicApi`、Adapter 切替 | `56d429ff` |
| 0.4 | ADR 0016 HandlingOrchestrator tx 境界起票 (H4/T2) | 単一 DB.localTx + ベストエフォート補償ログ案採用、5 観点比較で Outbox 案却下、実装は IT9 申し送り | `a797dae7` |
| 0.5 | TrackingExceptionEvent PK id 化 (H5/T8) | `TrackingExceptionEventId` opaque type 新設、PK 直接更新化、複合キー UPDATE 廃止 | `cf5ba5e2` |
| 0.6 | TrackingExceptionSpec 同値クラステスト (H7/M6) | +6 件追加 (CustomsHold / Damage 同値 / 3 例外型 escalationFlag デフォルト false / 再解決上書き仕様) | `3d5eb80b` |
| 0.7 | 例外対応取消し + 補足コメント追記 (H9) | `cancelExceptionResolution` / `appendResolutionComment` + 2 routes + UI 動線、Unit +4 件 | `ccfc330c` |
| 0.8 | Delay UI 拡張 + 意味ある値の通知 (H10/T7/P8) | RecordExceptionFormData に newEstimatedArrival / responsePlan 追加、`DelayResponsePlan` object (4 種定型)、JS 表示制御 | `8f037507` |
| 0.9 | README プロジェクト進捗反映 (H11) | Phase 1-4 × Release × IT 一覧表、累計 81 SP、設計ドキュメントへのリンク委譲 | `9bd295cf` |
| 0.10 | EitherValues 移行 (H12) | TrackingCommandServiceSpec の `@unchecked` パターン計 16 箇所を `.value` / `.left.value` に統一 | `275e3191` |
| 0.11 | HandlingCargoQueryPort + routeDeviation 自動判定 (T3) | Port + Adapter 新設、Orchestrator で Itinerary.isOnRoute 経由判定、Unit +3 件 | `79477c4f` |
| 0.12 | 設計ドキュメント反映 (T6) | data-model / domain-model / ui_design 3 文書を ADR 0019 整合に修正、Role 6 箇所統一 | `e73ca72d` |
| 0.13 | CLAUDE.md TDD コミット規律 (H6) | Red→Green 分離 or コミットメッセージ明記、原則 + Conventional Commits 例 + 例外規定 | `70a82d17` |
| 0.14 | ADR 0020 公開追跡画面例外表示方針 (H8) | 段階的開示 = ステータスバッジ + 簡易メッセージ + 連絡先のみ公開、対応詳細は社内画面のみ | `096c3be6` |
| 0.15 | ADR 0019 Payment 集約方針 (S3-1/2/3) | **案 B 採択** = Invoice 集約内 paymentStatus + 3 メソッド、Payment 独立集約は作らない | `096c3be6` |

### US22 法人割引適用（1.x、5/5、3 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 1.1 | BillingCargoSnapshot 拡張 | `corporateDiscountRate: Option[BigDecimal]` 追加、Shipper.discountRate から取得 |
| 1.2 | BillingCommandService.generate 改修 | snapshot 優先 + command.discountRate fallback、UI 入力依存ゼロ |
| 1.3 | Discount 明細追加 | `appendDiscountLineItem` ヘルパ、`LineItemCategory.Discount` + 負値 amount |
| 1.4 | billing/detail.scala.html 拡張 | 4 行構成（割引適用前/割引率＋バッジ/割引額/割引適用後）|
| 1.5 | テスト追加 | US22 シナリオ 4 件 (None=0% / 15% / 30% / snapshot 優先) |

### US23 精算処理（2.x、11/11、6 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 2.1 | Invoice 集約に支払メソッド追加 | PaymentStatus に NotIssued、Invoice に dueDate/paymentReference、issuePayment/confirmPayment/markOverdue、InvalidPaymentStateTransition Error、Snapshot 追随、Unit +6 件 |
| 2.2 | Flyway V23 invoice 拡張 | due_date + payment_reference 列追加、CHECK 制約に NotIssued 追加 |
| 2.3 | ScalikeJdbcInvoiceRepository 拡張 | rowTo / save の新フィールド対応、楽観ロック維持 |
| 2.4 | BillingCommandService.issuePayment | withOptimisticLock 適用、PaymentRequested 通知連携、Unit +3 件 |
| 2.5 | confirmPayment + Cargo.markSettled | Cargo.markSettled 追加、BookingPublicApi.markSettled 経由連携、Unit +2 件 |
| 2.6 | detectOverdue | 一括 Overdue 化バッチ API、Cron 連携は IT9 申し送り、Unit +2 件 |
| 2.7 | NotificationType + Flyway V27 | PaymentRequested / PaymentConfirmed / OverdueAlerted の 3 種追加、CHECK 拡張 |
| 2.8 | 請求書詳細画面に支払欄統合 | 5 種ステータスバッジ + 状態別フォーム（NotIssued→支払発行 / Pending\|Overdue→入金確認）、Settlement/MasterAdmin ロール制御 |
| 2.9 | MailNotificationPort + ADR 0018 | trait + LoggingAdapter、3 メソッド (sendPaymentRequested/Confirmed/sendOverdueAlert) 連携、IT9 で Pekko Mail/SES |
| 2.10 | 最終テスト拡充 | MailPort 送信検証 3 件追加、Billing Unit 計 29 件 Green、ScalikeJdbcInvoiceRepositoryIT / Playwright E2E は IT9 申し送り |
| 2.11 | Flyway V28 payment テーブル drop | ADR 0019 案 B 採択により未使用 payment テーブル削除 |

## 主要成果

### ADR（5 件起票・承認）

| ADR | 決定内容 |
|-----|---------|
| [0016](../adr/0016-handling-orchestrator-transaction-boundary.md) | HandlingOrchestrator は単一 DB.localTx + ベストエフォート補償ログ |
| [0017](../adr/0017-booking-public-api-port.md) | Booking Context に公開 Port `BookingPublicApi` 導入 |
| [0018](../adr/0018-mail-notification-port.md) | Billing Context に `MailNotificationPort` 導入、IT9 で実 Adapter 連携 |
| [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) | Payment は Invoice 集約内のステータス（案 B 採択、独立集約化しない）|
| [0020](../adr/0020-public-tracking-exception-display.md) | 公開追跡画面では例外バッジ + 簡易メッセージ + 連絡先のみ公開 |

### Flyway 新規（4 件）

| Migration | 内容 |
|-----------|------|
| V23 | invoice テーブルに due_date / payment_reference 追加、CHECK 制約に NotIssued |
| V26 | notification_log の LossEscalated → LostEscalated rename |
| V27 | notification_log CHECK に PaymentRequested / PaymentConfirmed / OverdueAlerted 追加 |
| V28 | 未使用 payment テーブル drop（ADR 0019 案 B 採択）|

### 新規 Port / Adapter

| Port | Adapter | 用途 |
|------|---------|------|
| `BookingPublicApi` (trait) | `BookingCommandService` (extends) | Booking 公開 API、他 Context は本 trait のみ依存 |
| `MailNotificationPort` (trait) | `LoggingMailNotificationAdapter` | 荷主向けメール送信 (IT8 はログのみ、IT9 で Pekko Mail/SES) |
| `HandlingCargoQueryPort` (trait) | `BookingCargoForHandlingAdapter` | Handling から Booking Cargo の itinerary 参照 |

### 新規値オブジェクト / ヘルパ

| 種類 | 名前 | 用途 |
|------|------|------|
| opaque type | `TrackingExceptionEventId` | tracking_exception_event PK の型安全表現 |
| object | `OptimisticLockOps` | `withOptimisticLock[A](label)` ヘルパ |
| object | `DelayResponsePlan` | 4 種定型 (Reroute / Express / BondedWarehouse / ContactShipper) + 日本語表示名 |

### Unit テスト

| 領域 | 追加件数 | 合計 |
|------|---------|------|
| Billing (BillingCommandServiceSpec + InvoiceSpec) | +19 | 29 |
| Tracking (TrackingCommandServiceSpec + TrackingExceptionSpec) | +10 | 31 |
| Handling (HandlingOrchestratorSpec) | +3 | 6 |
| Shared (OptimisticLockOpsSpec) | +5 | 5 |
| **合計** | **+37** | - |

### ドキュメント

- README.md にプロジェクト進捗セクション追加
- CLAUDE.md に TDD コミット規律セクション追加
- data-model.md / domain-model.md / ui_design.md を ADR 0019 案 B に整合反映
- retrospective-8.md / iteration_report-8.md（本書）新設

## DoD (Definition of Done) 達成状況

| 項目 | 状態 | 備考 |
| :--- | :---: | :--- |
| US22 受入条件全達成 | ✅ | 4 件 Unit カバー、UI 4 行表示 |
| US23 受入条件 1 (支払発行) | ✅ | issuePayment 完了、PaymentRequested 通知 + メール送信 |
| US23 受入条件 2 (入金確認) | ✅ | confirmPayment 完了、Cargo.Settled 遷移 + PaymentConfirmed 通知 |
| US23 受入条件 3 (決済機関連携) | 🔄 | **手動 referenceCode 入力に縮小** (IT9 申し送り) |
| US23 受入条件 4 (期限超過) | ✅ | detectOverdue + OverdueAlerted 通知 (Cron 連携 IT9 申し送り) |
| 0.x 申し送り 15 件全消化 | ✅ | H1-H12 + T3 + T6 + ADR 0019/0020 |
| Phase 4 完了 | ✅ | IT7-IT8 全 SP 消化 |
| Release 2.0 GA リリースゲート | 🔄 | コードレベルは到達、ステージング/本番デプロイは IT9 |
| 設計ドキュメント整合性 | ✅ | 0.12 で data/domain/ui 反映済 |

## ベロシティ・統計

| イテレーション | 計画 SP | 完了 SP | 達成率 |
| :--- | :---: | :---: | :---: |
| IT1 | 6 | 6 | 100% |
| IT2 | 13 | 13 | 100% |
| IT3 | 11 | 11 | 100% |
| IT4 | 12 | 12 | 100% |
| IT5 | 14 | 14 | 100% |
| IT6 | 14 | 14 | 100% |
| IT7 | 12 | 12 | 100% |
| **IT8** | **9** | **9** | **100%** |
| **累計** | **91** | **91** | **100%** |

平均ベロシティ: **11.4 SP/IT**（91 / 8）。IT8 は新規 9 SP + 申し送り 15 件で実質作業量は平均超過。

## IT9 申し送り（合計 9 件 + マルチパースペクティブレビュー 6 件 = 15 件）

> **本日解消済 (3 件は IT9 申し送りから除外)**: Flyway 採番ルール明文化は CLAUDE.md 追記済 (`6f498b0e`)、レビュー H2 / H3 / H5 / M1 / M2 / M4 / M5 の 7 件は実装内対応済。

### 当初の IT8 申し送り (再優先順序)

| # | 項目 | 優先度 |
|---|------|------:|
| 1 | Playwright E2E 4 件 (US22 + US23 各シナリオ) | 高 |
| 2 | Shipper 法人マスタ登録 UI 整備 (E2E 前提) | 高 |
| 3 | HandlingOrchestrator 単一 TX 化 (ADR 0016 実装) | 高 (本日レビュー H1) |
| 4 | MailNotificationPort の Pekko Mail / SES 連携 | 中 |
| 5 | ScalikeJdbcInvoiceRepositoryIT 拡張 (Testcontainers) | 中 |
| 6 | detectOverdue Cron 連携 (Pekko Scheduler) | 中 |
| 7 | US23 受入条件 3 拡張 (決済機関連携 / Stripe / GMO 等) | 中 |
| 8 | ArchUnit ルール拡張 (booking.application.commandservices 外部参照禁止) | 低 |
| ~~9~~ | ~~Flyway 番号採番ルール CLAUDE.md 追記~~ | ✅ 本日解消 (`6f498b0e`) |

### マルチパースペクティブレビューで追加発見 (6 件)

| # | 観点 | 内容 | 優先度 |
|---|------|------|------:|
| R1 | architect | ADR 0016 案 A 実装 (上記 3 と同一統合) | 高 |
| R2 | user-rep | 入金消込 CSV 取込 UI (Stripe/GMO 本実装までのブリッジ) | 高 |
| R3 | architect | 公開 Port vs 入力 Port 規約 ADR 化 + ArchUnit | 中 |
| R4 | tester | Refunded 状態遷移 + Lost 通知連携テスト 2 件追加 (Refund 機能本実装と併せて) | 中 |
| R5 | user-rep | 例外取消し動線の権限明文化 + audit_log テーブル化 | 中 |
| R6 | tester | テストピラミッド E2E 偏重リカバリ計画を IT9 計画ドキュメントに明示 (上記 1 と統合) | 低 |

## 関連ドキュメント

- [IT8 計画](./iteration_plan-8.md)
- [IT8 ふりかえり (KPT)](./retrospective-8.md)
- [リリース計画](./release_plan.md)
- ADR [0016](../adr/0016-handling-orchestrator-transaction-boundary.md) / [0017](../adr/0017-booking-public-api-port.md) / [0018](../adr/0018-mail-notification-port.md) / [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) / [0020](../adr/0020-public-tracking-exception-display.md)
- [IT7 完了報告書](./iteration_report-7.md)
