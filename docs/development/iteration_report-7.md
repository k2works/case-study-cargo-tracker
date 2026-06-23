---
title: イテレーション 7 完了報告書
date: 2026-06-23
---

# イテレーション 7 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT7 |
| 期間 | 2026-09-14 〜 2026-09-27（計画）/ 1 日（AI ペアプロ実績、Ralph Loop 自律実行） |
| ゴール | US19 遅延例外 + US20 破損・紛失例外（計 12 SP）を完成、Phase 4 着手、IT6 申し送り 16 件 (H1-H8 + M6/M7/M10 + O3 + ADR 適用 4 種 + SonarQube 再スキャン) を全消化 |
| 計画 SP | 12（US19: 6 + US20: 6） |
| 実績 SP | 12 |
| 達成率 | 100% |
| 連続コミット数 | 26（aff2405d 〜 4c25f664） |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US19 | 遅延例外を処理する | ✅ 完了 | 6 | 6 |
| US20 | 破損・紛失例外を処理する | ✅ 完了（US19 統合実装） | 6 | 6 |
| **合計** | | | **12** | **12** |

## タスク実績

機能タスク 11 件 + IT6 申し送り 16 件 = **27 タスク完了**。

### IT6 申し送り（0.x、16/16 全消化）

| # | タスク | 完了内容 |
|---|--------|---------|
| 0.1 | ArchUnit `contexts` 拡張 (H1) | `HexagonalArchitectureSpec` ルール 3 の `contexts` に billing/handling/tracking を追加（commit `4b4bb0f1`） |
| 0.2 | `BillingCargoQueryPort` ACL (H2) | `BillingCargoQueryPort` trait + `BillingCargoSnapshot` 値オブジェクト + `BookingCargoQueryAdapter` (`billing.infrastructure.acl`) 新設、`BillingCommandService` を Port 依存にリファクタ（commit `4b4bb0f1`） |
| 0.3 | `HandlingOrchestrator` + ACL ports (H3 / IT5 申し送り) | `TrackingLookupPort` + `BookingNotificationPort` + 各アダプター実装、`HandlingOrchestrator` で Handling 登録 → Tracking event → Booking 通知 → Claim 時 completeDelivery を一括実行、ArchUnit ルール 4 に Orchestrator/Input サフィックス追加（commit `a2e4be5f`、※単一 DB.localTx 化は IT8 ADR 0016 持ち越し） |
| 0.4 | ADR 0015 Money 統一 (H4) | `billing/Money.scala` (opaque type Long) 削除、`shared.domain.Money` に `unsafeFromJpy` + `multiplyByRate` extension、Invoice/Repository/View/Spec を shared Money 統一（commit `648ebbc8`） |
| 0.5 | Invoice Snapshot ADT (ADR 0014) | `Invoice.Snapshot` 新設、`reconstruct` 10 引数 → 1 引数化、`ScalikeJdbcInvoiceRepository.rowTo` リファクタ（commit `5949b0ac`） |
| 0.6 | Cargo Snapshot ADT (ADR 0014) | `Cargo.Snapshot` 新設、`reconstruct` 8 引数 → 1 引数化、`ScalikeJdbcCargoRepository` + 関連テスト 5 件リファクタ（commit `b98569e2`） |
| 0.7 | HandlingActivity Snapshot ADT (ADR 0014) | `HandlingActivity.RegisterRequest` + `HandlingActivity.Snapshot` 新設、`register` 8 引数 / `reconstruct` 9 引数を 1 引数化、Repository + CommandService + テスト 6 件リファクタ（commit `1e3677ad`） |
| 0.8 | 法人フラグ自動判定 (H5) | `BillingCargoSnapshot` に `isCorporate` 追加、`BookingCargoQueryAdapter` に `ShipperRepository` 注入し `ShipperType.Corporate` から自動判定、UI から「法人荷主」チェックボックス削除（commit `33346b7b`） |
| 0.9 | 料金内訳 + invoice_line_item (H6) | `InvoiceLineItem` 値オブジェクト + `LineItemCategory` enum、`PricingService.calculateActualWithBreakdown` で 3 明細（重量/距離/貨物種別）返却、`Invoice.lineItems` 追加、`billing/detail.scala.html` に内訳テーブル、Flyway V22 で既存 invoice_line_item テーブルに category カラム追加（commit `bc932fbe` + `bbb32cc7`） |
| 0.10 | PricingService 失敗系テスト (H7) | `calculateActual` の 4 つの失敗・分岐パスを検証（SameOriginAndDestination / candidateVoyage 伝播 / PriceCalculationFailed 伝播 / 空 itinerary）、AtomicReference で scalafix DisableSyntax.var 準拠（commit `14eccf85`） |
| 0.11 | OptimisticLock Either (H8) | `TrackingCommandService.updateStatus` で `OptimisticLockException` を「他のユーザーが更新したため再読込してください」Left に変換、NonFatal フォールバック追加（commit `0d0039e1`） |
| 0.12 | 荷受人確認 種別+値 + V18 (M6) | Flyway V18 で `recipient_confirmation_type` カラム追加（CHECK 制約）、`RecipientConfirmationType` enum (Signature/Stamp/IdCard/Code) 新設、`HandlingActivity` 拡張 + `RecipientConfirmationTypeRequired` エラー、UI フォーム 2 フィールド化（commit `c365536e`） |
| 0.13 | 手動更新理由 + Role 制御 (M7) | `ManualStatusUpdateFormData` に `reason` 必須追加、`updateStatus` を `Tracker`/`MasterAdmin` 限定、`canManualUpdate` フラグで詳細画面のボタン+モーダル表示制御、`NotificationPayload.ManualStatusUpdated.reason` 追加（commit `4fad2595`） |
| 0.14 | Itinerary leg + V19 (O3 部分) | `ItineraryLeg` 値オブジェクト新設、`Itinerary` 拡張（legs / isOnRoute / expectedLocations）、Flyway V19 `cargo_itinerary_leg` テーブル、`ScalikeJdbcCargoRepository` 2 段読込 + delete-then-insert 同期（commit `99fb8a8a`、※routeDeviation 自動判定は IT8 T3 持ち越し） |
| 0.15 | ユビキタス言語統一 (M10) | Handling 荷役登録 `Claim（引取）` → `Claim（引取作業）`、ヘルプ「引取作業 (Claim) 必須」、`NotificationType.DeliveryCompleted` コメントに 3 視点（社内/荷主/作業）の整理を追記（commit `72d795d6`） |
| 0.16 | SonarQube 再スキャン準備 | ADR 0014 ステータスを「承認」+ 4 集約の Snapshot 適用結果 + commit ハッシュ記録、ADR 0014 / 0015 共に承認状態に更新（commit `8e6f3a01`、※実機スキャナ実行は IT8 T5 持ち越し） |

### US19 遅延例外処理（6 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 1.1 | TrackingExceptionEvent ドメイン | `ExceptionType` enum (Delay / Damage / Lost / CustomsHold) + `TrackingExceptionEvent` エンティティ (exceptionType / location / occurredAt / description / escalationFlag / resolvedAt / resolutionNotes)、`TrackingActivity.addException` / `resolveException` / `hasActiveException` + `deriveStatus` 拡張（commit `46d604ff`） |
| 1.2 | Flyway V20 | `tracking_exception_event` テーブル (data-model.md L1015 準拠、CHECK 制約付)（commit `46d604ff`） |
| 1.3 | TrackingCommandService.recordException | `appendException` / `updateExceptionResolution` を Repository に追加、ScalikeJdbc 実装は共通ヘルパ `lockedTrackingId` / `bumpVersion` で楽観ロック処理を集約、`RecordExceptionCommand` / `ResolveExceptionCommand` DTO 新設（commit `7d60a6f3`） |
| 1.4 | DelayNotified 通知 | `NotificationType` に DelayNotified / DamageReported / LossEscalated / ExceptionResponded 追加、Payload 4 case class + JSON 直列化、`BookingCommandService` に `logDelayNotification` / `logDamageReport` / `escalateLoss` / `logExceptionResponse` + 共通 `findCargo` ヘルパ（commit `3fb40bab`） |
| 1.5 | Flyway V21 CHECK 拡張 | `notification_log` CHECK 制約を 11 種に拡張（commit `3fb40bab`） |
| 1.6 | 追跡詳細 UI モーダル | routes 2 件追加 (POST exceptions / POST exceptions/:index/resolve)、TrackingController に recordException/resolveException アクション (Role 制限 + CSRF + Clock 注入)、例外種別ごとに通知ログ自動連携、TrackingResult/QueryService に exceptions 追加、詳細画面に例外履歴テーブル + 記録モーダル + 対応報告フォーム（commit `bb001ca2`） |
| 1.7 | 統合テスト | TrackingCommandServiceSpec に 4 件追加 (Delay→InException、Lost→escalationFlag、解決後 hasActiveException=false、範囲外 index は Left)（commit `60252338`、※Playwright E2E は IT8 T4 持ち越し） |

### US20 破損・紛失例外処理（6 SP、US19 統合実装）

| # | タスク | 完了内容 |
|---|--------|---------|
| 2.1 | Damage/Lost シナリオ | US19 1.1 の `TrackingExceptionEvent` で全 4 種展開、`Lost` 時 `escalationFlag = true` 強制ロジック（commit `46d604ff`） |
| 2.2 | escalateException | US19 1.4 で `BookingCommandService.escalateLoss` として実装（commit `3fb40bab`） |
| 2.3 | UI Damage/Lost 選択 | US19 1.6 の例外記録モーダルで全 4 種 select + Lost 選択時の緊急エスカ自動（commit `bb001ca2`） |
| 2.4 | 補償方針 + 通知ペイロード | US19 1.4 + 1.6 で resolution_notes 永続化 + DamageReported/LossEscalated/ExceptionResponded ペイロード（commit `3fb40bab` + `bb001ca2`） |
| 2.5 | 統合テスト | US19 1.7 の TrackingCommandServiceSpec で Lost ケース検証（commit `60252338`） |

## テスト結果

| メトリクス | 値 |
|-----------|-----|
| ユニットテスト数 | 371 (前回 354 → +17) |
| Suites | 68 |
| 失敗 | 0 |
| ArchUnit ルール 1-5 | 全 pass（ルール 3 / 4 拡張済） |
| scalafmt | 通過 |
| scalafix | 通過 |
| Testcontainers IT | V18-V22 全適用確認 |

## テスト累計推移

| イテレーション | テスト数 |
|---------------|---------|
| IT1 | 35 |
| IT2 | 78 |
| IT3 | 224 |
| IT4 | 288 |
| IT5 | 323 |
| IT6 | 354 (Unit) + 36 (E2E) |
| **IT7** | **371** (Unit) + 36 (E2E、新規追加なし) |

## SonarQube Quality Gate

実機再スキャン未実施（IT7 T5 持ち越し）。構造的には IT6 で残存した MAJOR Code Smell 4 件 (Cargo / Invoice / HandlingActivity.register / HandlingActivity.reconstruct のパラメータ過多) を ADR 0014 Snapshot ADT 適用で全件解消済。

## Flyway マイグレーション

IT7 で追加された 4 件:

| Version | ファイル | 内容 |
|---------|---------|------|
| V18 | add_handling_activity_recipient_confirmation_type.sql | handling_activity に recipient_confirmation_type カラム追加 (CHECK 制約) |
| V19 | create_cargo_itinerary_leg.sql | 経路区間別の cargo_itinerary_leg テーブル新設 |
| V20 | create_tracking_exception_event.sql | tracking_exception_event テーブル新設 |
| V21 | notification_log_extend_for_us19_us20.sql | notification_log CHECK 制約を 11 種に拡張 |
| V22 | extend_invoice_line_item_category.sql | 既存 invoice_line_item に category カラム追加 |

## ADR

| ADR | 状態 | 内容 |
|-----|------|------|
| 0014 | 承認 | 集約 Snapshot ADT 導入。IT7 で 4 集約 (Invoice / Cargo / HandlingActivity register / HandlingActivity reconstruct) 適用済 |
| 0015 | 承認 | Billing Money を `shared.domain.Money` に一本化、`multiplyByRate` extension 追加 |

## アーキテクチャ進化

### コンテキスト境界の強化

ArchUnit ルール 3 の対象コンテキストを `auth / booking / estimation / routing / shipper` の 5 件 → `auth / billing / booking / estimation / handling / routing / shipper / tracking` の 8 件に拡張。以下の ACL ポート + アダプターを追加：

| Port | Adapter | 目的 |
|------|---------|------|
| `BillingCargoQueryPort` | `BookingCargoQueryAdapter` | Billing が Cargo + Shipper 属性を Snapshot で取得 |
| `TrackingLookupPort` | `TrackingAdapter` | Handling が tracking 番号→bookingId 解決 + イベント追記 |
| `BookingNotificationPort` | `BookingAdapter` | Handling が Booking 通知 + Claim 時 completeDelivery |

これにより `handling.application` / `billing.application` から他コンテキスト domain への直接依存は消失し、ACL アダプターの 3 点に集約された。

### Snapshot ADT パターン

`Cargo.Snapshot` (8 field) / `Invoice.Snapshot` (10 field) / `HandlingActivity.Snapshot` (9 field) + `HandlingActivity.RegisterRequest` (8 field) を導入。`reconstruct(snapshot)` 1 引数化で SonarQube パラメータ過多 Code Smell を構造的に解消。

### Money 単通貨化

`billing.domain.model.valueobjects.Money` (opaque type Long) を削除し、`shared.domain.Money` (case class + currency + amount) に統一。`multiplyByRate` extension を共有カーネルに集約し、Estimation / Billing 双方で再利用可能化。

## 機能・UI 進化

### 追跡詳細画面 (`/tracking/:trackingNumber`)

- **状態手動更新モーダル**: `reason` 必須化、`Tracker`/`MasterAdmin` 限定
- **例外記録モーダル** (NEW US19): 4 種選択 (Delay / Damage / Lost / CustomsHold)、Lost で自動エスカレーション
- **例外履歴テーブル** (NEW US19): #/種別/発生時刻/場所/説明/緊急バッジ/状態/対応報告フォーム
- **対応報告 (resolveException)**: 行内 textarea + submit、resolution_notes 永続化 + ExceptionResponded 通知

### 請求書詳細画面 (`/billing/invoices/:id`)

- **料金内訳テーブル** (NEW IT7 0.9): 種別 / 明細 / 金額 (JPY) を 3 明細表示（重量料金 / 距離料金加算 / 貨物種別加算）
- **法人バッジ自動表示**: Shipper 属性から自動判定（UI から手動入力欄削除）

### 荷役登録画面 (`/handling/new`)

- **荷受人確認 2 フィールド構成** (US16 / IT7 0.12): 種別 select (署名 / 受領印 / 身分証 / 引取コード) + 値 textarea、Claim 時のみ表示

## 追加機能の通知種別

`NotificationType` に 4 種追加 (DelayNotified / DamageReported / LossEscalated / ExceptionResponded)、Payload + JSON 直列化済。`notification_log` テーブルは CHECK 制約 11 種に拡張。

## 未完了・持ち越し項目（IT8 で対応）

| # | 項目 | 推定 SP | 参照 |
|---|------|---------|------|
| T1 | マイグレ着手前 grep チェック慣行化 | - | retrospective-7.md T1 |
| T2 | HandlingOrchestrator の単一 DB.localTx 化 (ADR 0016 起票) | 3 | retrospective-7.md T2 / P3 |
| T3 | routeDeviation 自動判定完成 (HandlingCargoQueryPort 経由) | 2 | retrospective-7.md T3 / 0.14 ※ |
| T4 | Playwright E2E US19/US20 4 シナリオ | 2 | retrospective-7.md T4 / 1.7 ※ |
| T5 | SonarQube 実機再スキャン + Quality Gate 数値記録 | 1 | retrospective-7.md T5 / 0.16 ※ |
| T6 | 設計ドキュメント正式反映 (data-model / domain-model / ui_design) | 2 | retrospective-7.md T6 |
| T7 | 遅延通知 newEstimatedArrival UI 入力欄 | 1 | retrospective-7.md T7 / P8 |
| T8 | TrackingExceptionEvent に内部 ID 付与 | 2 | retrospective-7.md T8 / P10 |

## ふりかえり

詳細は [retrospective-7.md](./retrospective-7.md) を参照。

**サマリ**: Keep 7 件 / Problem 11 件 / Try 8 件 (T1-T8) を抽出。Phase 4 着手 + 申し送り全消化により Release 1.0 ベース機能はほぼ完了し、IT8 では拡張機能 (US22 法人割引 / US23 支払い確認) + 持ち越し改善 (T1-T8) に集中可能。

## 更新履歴

| 日付 | 変更内容 | 著者 |
|------|---------|------|
| 2026-06-23 | IT7 完了報告書初版作成 | Ralph Loop 自律実行 |
