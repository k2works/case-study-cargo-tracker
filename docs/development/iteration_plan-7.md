---
title: イテレーション 7 計画
date: 2026-06-23
---

# イテレーション 7 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT7 |
| **期間** | Week 13-14（2026-09-14 〜 2026-09-27、計画 2 週間 / AI ペアプロ実績は 1〜2 日想定） |
| **ゴール** | US19 遅延例外 + US20 破損・紛失例外 を実装し例外管理機能を完成、IT6 developing-review 高優先 8 件 + IT5 申し送り 3 件 + ADR 0014 Snapshot ADT 適用を冒頭で解消し Release 2.0 GA 基盤を整える |
| **目標 SP** | 12（US19: 6 + US20: 6） |

---

## ゴール

### イテレーション終了時の達成状態

1. **例外管理機能 (Phase 4 1/2)**: 遅延・破損・紛失の 3 種類例外を記録 → 貨物状態「例外発生」に遷移 → 荷主通知 → 対応報告まで一気通貫
2. **アーキテクチャ堅牢化**: ArchUnit が新規 4 コンテキスト (billing/handling/tracking/notification) を境界検査対象とし、Billing → Booking 直結を BillingCargoQueryPort 経由に分離、HandlingOrchestrator で単一 DB.localTx 境界化
3. **集約 reconstruct の Snapshot ADT 化 (ADR 0014)**: Invoice / Cargo / HandlingActivity の reconstruct を Snapshot 引数 1 個に統一し SonarQube MAJOR Code Smell 4 件解消
4. **業務適合性修正**: 請求書発行で法人フラグ自動判定、請求書詳細に料金内訳表示、荷受人確認を種別+値の 2 フィールド構成、手動更新に理由欄追加 + Tracker ロール限定

### 成功基準

- [ ] US19/US20 受け入れ基準を全て満たす
- [ ] IT6 developing-review 高優先 8 件全て解消（H1〜H8）
- [ ] IT5 未消化申し送り 3 件解消（0.2 H6 / 0.3 H3 / 0.10 O3）
- [ ] ADR 0014 Snapshot ADT を Invoice / Cargo / HandlingActivity に適用
- [ ] ArchUnit が 9 コンテキストすべてを検査対象とし 5/5 緑
- [ ] SonarQube MAJOR Code Smell 0 件、Coverage 80% 以上維持
- [ ] Playwright E2E 全件 PASS + US19/US20 E2E 追加（3 件以上）

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 |
|----|-------------------|----|----|
| US19 | 遅延例外を処理する | 6 | 必須 |
| US20 | 破損・紛失例外を処理する | 6 | 必須 |
| **合計** | | **12** | |

### ストーリー詳細

#### US19: 遅延例外を処理する

> **追跡管理者として**、輸送中に遅延が発生した場合、例外種別「遅延」として記録し、荷主への通知と対応内容を管理したい。なぜなら、遅延情報を速やかに荷主に伝え、対応策（代替ルート等）を迅速に提示できるからだ。

**受入条件**:

1. 追跡番号と例外種別「遅延」・発生状況（場所・日時・理由）を記録できる
2. 記録後、貨物状態が「例外発生」(`InException`) に更新される
3. 荷主に遅延発生の通知が送信される
4. 対応内容（新しい到着予定日・対応方針）を入力して荷主に対応報告を送信できる
5. 例外対応履歴が記録される

#### US20: 破損・紛失例外を処理する

> **追跡管理者（または荷役作業員）として**、輸送中に破損または紛失が発生した場合、例外種別「破損」または「紛失」として記録し、関係者に緊急通知を送りたい。なぜなら、重大な例外は即座に全関係者に共有し、保険手続き・補償対応・代替措置を迅速に開始できるからだ。

**受入条件**:

1. 追跡番号と例外種別「破損」または「紛失」・発生状況を記録できる
2. 記録後、貨物状態が「例外発生」(`InException`) に更新される
3. 例外種別「紛失」の場合、緊急フラグが設定されて管理職への escalation 通知が送信される
4. 荷主に破損・紛失発生の通知が送信される
5. 対応内容（補償方針等）を入力して荷主に報告を送信できる

---

### タスク

#### 0. IT6 申し送り（developing-review 高優先 8 件 + IT5 未消化 3 件 + ADR 適用）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 0.1 | ArchUnit `contexts` に billing/handling/tracking を追加し境界違反を可視化（H1） | 2h | [x] |
| 0.2 | `BillingCargoQueryPort` (Billing 側 trait) + Booking 側 ACL アダプター実装、`BillingCommandService` の Cargo 直接結合を解消（H2 / IT5 申し送り 0.2 部分流用） | 5h | [x] |
| 0.3 | `HandlingOrchestrator` (Application Service) を新設し、Handling 登録 + Tracking event 追記 + Booking 通知 + completeDelivery を単一 `DB.localTx` で実行（H3 / IT5 申し送り 0.3）。`HandlingController` の Claim 連結を Orchestrator 呼出に置換 | 6h | [ ] |
| 0.4 | ADR 0015 起票「Billing は単通貨 JPY、shared.domain.Money に一本化」+ `BillingMoney` 削除、`shared.domain.Money` に `multiplyByRate` extension を追加（H4） | 4h | [ ] |
| 0.5 | ADR 0014 Snapshot ADT 適用: `Invoice.Snapshot` 新設 → `ScalikeJdbcInvoiceRepository` リファクタ | 3h | [x] |
| 0.6 | ADR 0014 Snapshot ADT 適用: `Cargo.Snapshot` 新設 → `ScalikeJdbcCargoRepository` + 関連テストリファクタ | 4h | [x] |
| 0.7 | ADR 0014 Snapshot ADT 適用: `HandlingActivity.Snapshot` + `RegisterRequest` 新設 → Repository + CommandService リファクタ | 4h | [x] |
| 0.8 | 請求書発行 UI から法人フラグ手入力欄を削除、`BillingShipperId` を Booking 経由で荷主属性 (`Shipper.shipperType`) から自動判定（H5） | 4h | [ ] |
| 0.9 | 請求書詳細画面に料金内訳（距離料金 / 重量料金 / 貨物種別料金）を表示、`PricingService.calculateActual` で `invoice_line_item` を生成しテーブル永続化（H6 / IT8 US22 前倒し候補） | 6h | [ ] |
| 0.10 | `PricingService.calculateActual` の失敗系テスト追加（無効ルート / 単価未登録 / 計算オーバーフロー）（H7） | 2h | [ ] |
| 0.11 | `TrackingCommandService.updateStatus` の `OptimisticLockException` を `Either[String, _]` に畳み込み、UI に「他のユーザーが更新したため再読込してください」を表示（H8） | 3h | [ ] |
| 0.12 | 荷役登録 UI: 荷受人確認を「種別 (署名 / 受領印 / 身分証 / コード) + 値」の 2 フィールド構成に変更、`HandlingActivity` に `recipientConfirmationType` 追加 + Flyway V18（M6） | 4h | [ ] |
| 0.13 | 追跡詳細の手動更新モーダルに「更新理由」必須フィールド追加 + `Role.Tracker / MasterAdmin` 限定でボタン表示制御（M7） | 3h | [ ] |
| 0.14 | `Itinerary` に leg 詳細（from/to UnLocode）追加し `HandlingCommandService.register` で routeDeviation を正式判定（O3 / IT5 申し送り 0.10）。Flyway V19 で `cargo_itinerary_leg` テーブル新設 | 5h | [ ] |
| 0.15 | ユビキタス言語統一: `DeliveryCompleted` (ドメイン) / 「引取作業」(UI) / 「配送完了」(通知) を「荷主視点 = 引取済」「社内視点 = 配送完了」で整理し view 文言を統一（M10） | 2h | [ ] |
| 0.16 | SonarQube 再スキャン + Quality Gate 確認、MAJOR Code Smell 0 件達成を ADR 0014 ステータス更新で記録 | 2h | [ ] |

**小計**: 59h

#### 1. US19 遅延例外処理（6 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 1.1 | Tracking Context 拡張: `TrackingExceptionEvent` エンティティ新設（domain-model.md L 準拠: `exceptionType: ExceptionType` / `location: TrackingLocation` / `occurredAt` / `description: Option[String]` / `escalationFlag: Boolean` / `resolvedAt: Option[Instant]`）、`ExceptionType` enum (Delay / Damage / Lost / CustomsHold) + `TrackingActivity.addException` / `resolveException` / `hasActiveException` / `TrackingStatus.InException` 導出ロジック | 5h | [ ] |
| 1.2 | Flyway V20: `tracking_exception_event` テーブル（data-model.md 準拠: `tracking_id` FK / `exception_type VARCHAR(50)` CHECK / `occurred_at` / `escalation_flag BOOLEAN` / `description VARCHAR(500)` / `resolved_at` / `resolution_notes TEXT` / 監査）+ ※location は IT7 で `location_unlocode` カラム追加し data-model.md にも反映 | 2h | [ ] |
| 1.3 | `TrackingCommandService.recordException(RecordExceptionCommand)` 実装: 楽観ロック付き、TrackingStatus を `currentStatus()` 経由で `InException` 導出 | 4h | [ ] |
| 1.4 | `BookingCommandService.logDelayNotification` + `NotificationType.DelayNotified` + `NotificationPayload.DelayNotified` (新到着予定日 / 対応方針 / 理由) | 3h | [ ] |
| 1.5 | Flyway V21: `notification_log` CHECK 拡張（`DelayNotified` / `DamageReported` / `LossEscalated` / `ExceptionResponded` 4 種追加） | 1h | [ ] |
| 1.6 | 追跡詳細画面 (`/tracking/:trackingNumber`) に「例外を記録」ボタン + モーダル（例外種別 Delay/Damage/Lost/CustomsHold / 場所 / 日時 / description）+ 「対応報告」ボタン + モーダル（resolution_notes）+ POST `/tracking/:trackingNumber/exceptions` / POST `.../exceptions/:eventId/resolve` ルート追加 (CSRF formField 必須) | 5h | [ ] |
| 1.7 | E2E + ユニットテスト（遅延記録 → InException 遷移 → DelayNotified ログ → 対応報告 → ExceptionResponded ログ） | 4h | [ ] |

**小計**: 24h

#### 2. US20 破損・紛失例外処理（6 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 2.1 | `ExceptionType.Damage` / `ExceptionType.Lost` (domain-model 命名準拠) を `TrackingExceptionEvent` シナリオに展開（US19 1.1 と統合済）、`Lost` 時の `escalationFlag = true` ロジック | 3h | [ ] |
| 2.2 | `BookingCommandService.escalateException` 実装: `Lost` 時に管理職 (`Role.MasterAdmin`) 向け escalation 通知 + `NotificationType.LossEscalated` ログ | 4h | [ ] |
| 2.3 | 追跡詳細画面の「例外を記録」モーダルで Damage / Lost を選択可能化、Lost 選択時に「緊急対応フラグ」表示 | 3h | [ ] |
| 2.4 | 補償方針入力フォーム（`resolution_notes` 永続化）+ `NotificationPayload.DamageReported` / `LossEscalated` 通知ペイロード | 4h | [ ] |
| 2.5 | E2E + ユニットテスト（破損記録 → InException 遷移 + DamageReported / 紛失記録 → escalationFlag + LossEscalated 管理職通知） | 4h | [ ] |

**小計**: 18h

#### タスク合計

| カテゴリ | SP | 理想時間 |
|---------|----|----|
| IT6 申し送り（0.x） | - | 59h |
| US19 遅延例外処理 | 6 | 24h |
| US20 破損・紛失例外処理 | 6 | 18h |
| **合計** | **12** | **101h** |

**1 SP あたり**: 約 8.4h（IT6 申し送り含む / 機能タスクのみなら 3.5h）
**進捗率**: 0% (0/12 SP)

> **IT7 スコープ外で IT8 / IT9 へ申し送り**:
>
> - US22 法人割引適用ロジック（割引内訳の請求書詳細表示は 0.9 で部分実装）
> - US23 支払い確認 + 精算処理
> - US10 経路条件再算出（IT9 予備）
> - SonarQube MAJOR 4 件以外の中長期コード品質改善（重複や複雑度）

---

## スケジュール

### Week 1（Day 1-5）

```mermaid
gantt
    title イテレーション 7 - Week 1
    dateFormat  YYYY-MM-DD
    section アーキ堅牢化
    ArchUnit 拡張 + Billing ACL          :d1, 2026-09-14, 1d
    HandlingOrchestrator + ADR 0015 Money :d2, after d1, 1d
    section Snapshot 適用
    Invoice/Cargo/HandlingActivity Snapshot :d3, after d2, 1d
    section 業務適合性 + テスト補強
    法人フラグ自動 + 料金内訳 + 失敗系テスト :d4, after d3, 1d
    OptimisticLock Either + 荷受人確認種別 + 手動更新理由 + Itinerary leg + 言語統一 :d5, after d4, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | 0.1 ArchUnit 拡張 / 0.2 BillingCargoQueryPort + ACL |
| Day 2 | 0.3 HandlingOrchestrator + 単一 DB.localTx / 0.4 ADR 0015 Money 統一 |
| Day 3 | 0.5-0.7 Snapshot ADT 適用 (Invoice / Cargo / HandlingActivity) |
| Day 4 | 0.8 法人フラグ自動 / 0.9 料金内訳 + invoice_line_item / 0.10 PricingService 失敗系テスト |
| Day 5 | 0.11 OptimisticLock Either / 0.12 荷受人確認種別 + V18 / 0.13 手動更新理由 + Tracker 限定 / 0.14 Itinerary leg + V19 / 0.15 言語統一 / 0.16 SonarQube 再スキャン |

### Week 2（Day 6-10）

```mermaid
gantt
    title イテレーション 7 - Week 2
    dateFormat  YYYY-MM-DD
    section US19 遅延例外
    TrackingExceptionEvent + V20      :a1, 2026-09-21, 1d
    recordException + 通知 + V21      :a2, after a1, 1d
    UI モーダル + E2E                  :a3, after a2, 1d
    section US20 破損・紛失例外
    Damage / Loss + escalateException :u1, after a3, 1d
    補償方針 + E2E + デモ準備          :u2, after u1, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | 1.1 TrackingExceptionEvent + ExceptionType / 1.2 V20 |
| Day 7 | 1.3 recordException 楽観ロック / 1.4 DelayNotified 通知 / 1.5 V21 CHECK 拡張 |
| Day 8 | 1.6 追跡詳細 UI モーダル / 1.7 US19 E2E + ユニットテスト |
| Day 9 | 2.1 Damage/Loss + 緊急フラグ / 2.2 escalateException / 2.3 UI 拡張 |
| Day 10 | 2.4 補償方針 + 通知 / 2.5 US20 E2E + 統合テスト + デモ準備 |

---

## 設計

### ドメインモデル

IT6 までで確立した 8 コンテキスト（Auth / Shipper / Estimation / Booking / Routing / Tracking / Handling / Billing）に対し、IT7 は **Tracking Context の `TrackingExceptionEvent` を本格活用**し、Booking 側で `BookingStatus.InException` 遷移と例外通知連携を整える。さらに **Snapshot ADT パターン（ADR 0014）** を Invoice / Cargo / HandlingActivity の reconstruct に適用、**Money 単通貨化（ADR 0015）** で shared.domain.Money に一本化、**Billing → Booking ACL（`BillingCargoQueryPort`）** と **HandlingOrchestrator** で集約間トランザクション境界を再設計する。

```plantuml
@startuml

title IT7 ドメインモデル全体図（例外処理 + ACL + Orchestrator + Snapshot ADT）

package "Shared Kernel" {
  class Money <<value>> {
    amount: Long
    currency: String
    --
    + jpy(amount): Either
    + multiplyByRate(r): Money
  }
  class Location <<value>> {
    unLocode
  }
  class PricingService <<service>> {
    + estimateCost(...)
    + calculateActual(spec): (Money, LineItems)
  }
}

package "Booking Context" {
  class Cargo <<aggregate root>> {
    bookingId
    status: BookingStatus
    itinerary: Option
    trackingNumber: Option
    invoiceId: Option
    version
    --
    + markException()
    + resolveException()
  }
  class "Cargo.Snapshot" as CargoSnapshot <<value>> {
    全永続化フィールド
    --
    + reconstruct(s)
  }
  class Itinerary <<value>> {
    legs: List[ItineraryLeg]
  }
  class ItineraryLeg <<value>> {
    legNo
    voyageNumber
    fromUnLocode
    toUnLocode
  }
  enum BookingStatus {
    Preliminary
    RouteProposed
    RouteAssigned
    Confirmed
    TrackingIssued
    InTransit
    Delivered
    InException
    Settled
    Cancelled
  }
  Cargo *-- BookingStatus
  Cargo o-- Itinerary
  Itinerary *-- "1..*" ItineraryLeg
  Cargo .. CargoSnapshot
}

package "Tracking Context" {
  class TrackingActivity <<aggregate root>> {
    trackingNumber
    bookingId
    transportStatus
    events
    exceptions
    version
    --
    + addEvent(e): Either
    + addException(ex): Either
    + resolveException(eventId, at): Either
    + hasActiveException(): Boolean
    + currentStatus(): TrackingStatus
  }
  class TrackingActivityEvent
  class TrackingExceptionEvent <<entity>> {
    eventId
    exceptionType
    location: TrackingLocation
    occurredAt
    description: Option
    escalationFlag
    resolvedAt: Option
    resolutionNotes: Option
  }
  enum ExceptionType {
    Delay
    Damage
    Lost
    CustomsHold
  }
  enum TrackingStatus {
    NotReceived
    Received
    Loaded
    OnboardCarrier
    Unloaded
    AwaitingClaim
    Claimed
    InException
    Unknown
  }
  TrackingActivity *-- "0..*" TrackingActivityEvent
  TrackingActivity *-- "0..*" TrackingExceptionEvent
  TrackingExceptionEvent --> ExceptionType
  TrackingActivity --> TrackingStatus
}

package "Handling Context" {
  class HandlingActivity <<aggregate root>> {
    bookingId
    eventType
    completionTime
    location
    voyageNumber
    operatorName
    routeDeviation
    recipientConfirmation
    recipientConfirmationType
    version
    --
    + register(req)
    + reconstruct(s)
  }
  class "HandlingActivity.Snapshot" as HASnap <<value>>
  class "HandlingActivity.RegisterRequest" as HAReq <<value>>
  enum RecipientConfirmationType {
    Signature
    Stamp
    IdCard
    Code
  }
  HandlingActivity .. HASnap
  HandlingActivity .. HAReq
  HandlingActivity --> RecipientConfirmationType
}

package "Billing Context" {
  class Invoice <<aggregate root>> {
    invoiceId
    cargoBookingId
    shipperId
    baseAmount: Money
    discountRate
    finalAmount: Money
    paymentStatus
    issuedAt
    paidAt
    version
    --
    + issue(snapshot)
    + reconstruct(s)
  }
  class "Invoice.Snapshot" as InvSnap <<value>>
  class BillingShipperId <<value>> {
    shipperId
    isCorporate
  }
  class InvoiceLineItem <<entity>> {
    seqNumber
    description
    amount: Money
  }
  Invoice *-- "1..*" InvoiceLineItem
  Invoice *-- BillingShipperId
  Invoice .. InvSnap
}

package "Booking ACL (Billing 側 Port)" {
  interface BillingCargoQueryPort <<port>> {
    + findByBookingId(bid): Option[CargoSummary]
  }
  class CargoSummary <<value>> {
    bookingId
    shipperId
    shipperType
    status
    routeSpec
    cargoSpec
    itinerary
  }
  BillingCargoQueryPort -- CargoSummary
}

package "Application Orchestration" {
  class HandlingOrchestrator <<service>> {
    + registerHandlingWithSideEffects(cmd):
       Either[String, Activity]
    --
    単一 DB.localTx 境界:
    HandlingActivity.save
    + TrackingActivity.appendEvent
    + Booking.logHandlingNotification
    + (Claim 時) Cargo.deliver
  }
}

Invoice ..> PricingService : calculateActual
Invoice ..> BillingCargoQueryPort : findByBookingId
TrackingActivity ..> Cargo : 状態同期（addException/resolveException 経由）
HandlingActivity ..> Cargo : (Claim) deliver via Orchestrator
HandlingOrchestrator ..> HandlingActivity
HandlingOrchestrator ..> TrackingActivity
HandlingOrchestrator ..> Cargo

note right of BookingStatus
  IT7 で InException 遷移を有効化
  TrackingActivity.addException 経由で
  Cargo.markException が呼ばれる
end note

note right of TrackingExceptionEvent
  IT7 新規本格活用
  (IT6 までは enum 定義のみ)
end note

note right of HandlingOrchestrator
  IT7 新規 (ADR 0016 候補 + H3 解消)
  Controller 経由 Handling 連結を
  単一 DB.localTx に集約
end note

note right of BillingCargoQueryPort
  IT7 新規 (H2 + IT5 H6 同時解消)
  Billing は Booking domain に直結せず
  Port 経由で Cargo Summary を取得
end note

note bottom of InvSnap
  ADR 0014 適用で 10 引数 reconstruct を
  1 引数 (Snapshot) に統一
  (Cargo / HandlingActivity も同様)
end note

@enduml
```

#### 不変条件（IT7 追加分）

1. `TrackingActivity.addException` は時系列順序を強制（最終イベントより未来の `occurredAt`）。違反時 `OutOfOrder` を返す
2. `TrackingActivity.addException` 成功時、`currentStatus()` が `InException` を導出し、`Cargo.markException()` がアプリ層で連動呼出される
3. `ExceptionType.Lost` の `TrackingExceptionEvent` は `escalationFlag = true` を強制
4. `TrackingActivity.resolveException(eventId, at)` は `resolvedAt = None` の未解決例外にのみ実行可、解決後 `currentStatus()` は最新の通常イベントから再導出
5. `TrackingActivity.hasActiveException` 真の間、`addEvent` は警告フラグ付きで受理（通常運用継続可だが UI で警告表示）
6. `Cargo.markException` は `InTransit / TrackingIssued / Delivered` のいずれかから `InException` に遷移可、それ以外は `InvalidStatusTransition`
7. `Cargo.resolveException` は `InException` 状態のみで実行可、`previousStatus` (InTransit or Delivered) に戻す
8. `Invoice.reconstruct(snapshot)` は `finalAmount == baseAmount.multiplyByRate(1 - discountRate)` を require で強制（バイパス防止）
9. `HandlingActivity.register(request)` は `eventType == Claim` のとき `recipientConfirmation` + `recipientConfirmationType` ともに必須
10. `Itinerary.legs` は 1 件以上を require、`legs(i).fromUnLocode == legs(i-1).toUnLocode`（接続性）を強制
11. `BillingCargoQueryPort.findByBookingId` の戻り値 `CargoSummary` は read-only スナップショット（Billing から Booking 集約を変更不可）
12. `HandlingOrchestrator.registerHandlingWithSideEffects` は単一 `DB.localTx` 境界で 4 操作を実行、いずれか失敗で全件ロールバック

#### BookingStatus 状態遷移マトリクス（IT7 拡張版）

| from \ to | InTransit | Delivered | **InException** | Settled | Cancelled |
|-----------|:---------:|:---------:|:---------------:|:-------:|:---------:|
| **TrackingIssued** | ✓（IT5）| ✓（US16）| **✓（US19/20）** | - | - |
| **InTransit** | - | ✓（US16）| **✓（US19/20）** | - | - |
| **Delivered** | - | - | **✓（US20 紛失）** | ✓（IT8） | - |
| **InException** | - | - | - | - | ✓（業務判断） |

太字は IT7 で新規追加する遷移（`* → InException`、US19/US20 経由）。`InException → InTransit/Delivered/TrackingIssued` への復帰は `resolveException` で前状態に戻す（不変条件 7）。

#### TrackingStatus 導出ロジック（IT7 拡張）

`TrackingActivity.currentStatus()` は以下の優先順位で導出:

1. **未解決の `TrackingExceptionEvent` が存在** → `InException`（IT7 新規）
2. 最終 `TrackingActivityEvent.eventType` から導出（IT5 既存）:
    - `Receive` → `Received`
    - `Load` → `Loaded`
    - `Unload` → `Unloaded`
    - `Claim` → `Claimed`
    - `Customs` → `InException`（IT5 既存。IT7 で `CustomsHold` 例外への移行を推奨）
3. イベントなし → `NotReceived`

### データモデル

V17 まで適用済の IT6 状態に対し、IT7 で **V18 / V19 / V20 / V21** を追加する。命名規約（単数形テーブル / `id BIGSERIAL PK + 業務キー UK` / `version INT` / 監査カラム / FK は `id` 参照）は data-model.md に準拠する。

#### V18: handling_activity.recipient_confirmation_type（US16 review M6 解消）

```sql
-- IT7 0.12: 荷受人確認の種別フィールド追加（IT6 review M6）
ALTER TABLE handling_activity
  ADD COLUMN recipient_confirmation_type VARCHAR(20)
    CHECK (recipient_confirmation_type IN ('Signature', 'Stamp', 'IdCard', 'Code'));
COMMENT ON COLUMN handling_activity.recipient_confirmation_type IS 'Claim 時のみ必須（recipient_confirmation と対）';
```

#### V19: cargo_itinerary_leg 構造拡張（IT5 申し送り 0.10 / O3 解消）

```sql
-- IT7 0.14: Itinerary に from/to UnLocode を追加し routeDeviation を正式判定
-- 既存 cargo_itinerary_leg (V10) は voyage_number のみ → from/to を追加
ALTER TABLE cargo_itinerary_leg
  ADD COLUMN from_unlocode VARCHAR(5),
  ADD COLUMN to_unlocode VARCHAR(5);
CREATE INDEX idx_cargo_itinerary_leg_from ON cargo_itinerary_leg (from_unlocode);
CREATE INDEX idx_cargo_itinerary_leg_to ON cargo_itinerary_leg (to_unlocode);
COMMENT ON COLUMN cargo_itinerary_leg.from_unlocode IS 'IT7: routeDeviation 判定用の出発港';
COMMENT ON COLUMN cargo_itinerary_leg.to_unlocode IS 'IT7: routeDeviation 判定用の到着港';
```

#### V20: tracking_exception_event（US19/US20、data-model.md L1015 準拠）

```sql
-- IT7 US19/US20: 追跡例外イベント
-- data-model.md は location カラムなしだが、ドメイン上 TrackingLocation を保持するため
-- location_unlocode を追加（同時に data-model.md にも反映）
CREATE TABLE tracking_exception_event (
  id BIGSERIAL PRIMARY KEY,
  tracking_id BIGINT NOT NULL REFERENCES tracking_activity (id) ON DELETE CASCADE,
  exception_type VARCHAR(50) NOT NULL
    CHECK (exception_type IN ('Delay', 'Damage', 'Lost', 'CustomsHold')),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  location_unlocode VARCHAR(5) NOT NULL,
  escalation_flag BOOLEAN NOT NULL DEFAULT FALSE,
  description VARCHAR(500),
  resolved_at TIMESTAMP WITH TIME ZONE,
  resolution_notes TEXT,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tracking_exception_tracking ON tracking_exception_event (tracking_id);
CREATE INDEX idx_tracking_exception_type ON tracking_exception_event (exception_type);
CREATE INDEX idx_tracking_exception_unresolved ON tracking_exception_event (tracking_id)
  WHERE resolved_at IS NULL;  -- 未解決例外の高速検索
```

#### V21: notification_log CHECK 拡張（US19/US20）

```sql
-- IT7 US19/US20: 例外通知 4 種を追加
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_type;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_type
    CHECK (type IN ('RouteNotified', 'BookingConfirmed', 'BookingCancelled',
                    'TrackingIssued', 'HandlingRecorded',
                    'ManualStatusUpdated', 'DeliveryCompleted',
                    'DelayNotified', 'DamageReported', 'LossEscalated', 'ExceptionResponded'));
```

#### 既存テーブル一覧（参考）

| テーブル | バージョン | IT |
|---------|----------|-----|
| user, shipper, cargo, voyage, carrier_movement, voyage_supported_cargo_type, estimate, route_candidate | V1-V8 | IT1-IT3 |
| route_candidate_selection / route_candidate_selection_leg | V9 | IT4 |
| cargo_itinerary_leg（voyage_number のみ） | V10 | IT4 |
| notification_log | V11 | IT4 |
| tracking_activity（+ cargo.tracking_number） | V12 | IT5 |
| handling_activity | V13 | IT5 |
| tracking_handling_event（+ notification_log CHECK 拡張） | V14 | IT5 |
| handling_activity.recipient_confirmation | V15 | IT6 |
| notification_log CHECK 拡張（ManualStatusUpdated / DeliveryCompleted） | V16 | IT6 |
| invoice / invoice_line_item / payment / cargo.invoice_id / invoice_id_seq | V17 | IT6 |
| **handling_activity.recipient_confirmation_type** | **V18** | **IT7** |
| **cargo_itinerary_leg.from/to_unlocode 追加** | **V19** | **IT7** |
| **tracking_exception_event** | **V20** | **IT7** |
| **notification_log CHECK 拡張（DelayNotified / DamageReported / LossEscalated / ExceptionResponded）** | **V21** | **IT7** |

### ユーザーインターフェース

ui_design.md L82（追跡詳細）の「状態更新・例外登録（管理者）」機能を IT7 で具体化する。追跡詳細画面に例外履歴セクション + 例外記録モーダル + 対応報告モーダルを追加、荷役登録に確認種別セレクト、ナビバーに変更なし。

#### ビュー

```plantuml
@startsalt
{+
  追跡詳細（拡張 / `/tracking/:trackingNumber`、US19/US20）
  {+
    {/ <b>CargoTracker</b> | ダッシュボード | 貨物追跡 | 荷役管理 | [ログアウト] }
    {
      追跡番号 | "TN-000123"
      現在状態 | "<b>InException</b>"
      現在位置 | "USNYC"
    }
    ---
    {
      <b>追跡イベント履歴</b>
      | <b>発生時刻</b> | <b>種別</b> | <b>場所</b> | <b>航海番号</b> |
      | 2099-09-01 10:00 | Receive | JPYOK | - |
      | 2099-09-05 14:30 | Load | JPYOK | VY-001 |
    }
    ---
    {
      <b>例外履歴</b>
      | <b>発生時刻</b> | <b>種別</b> | <b>場所</b> | <b>説明</b> | <b>緊急</b> | <b>解決日時</b> |
      | 2099-09-08 12:00 | Delay | USNYC | "通関手続き遅延" | - | - |
    }
    ---
    [別の貨物を追跡] | [状態を手動更新] | [<b>例外を記録</b>] | [<b>対応報告</b>]
  }
}
----------------
{+
  例外記録モーダル（拡張、US19/US20）
  {+
    {
      例外種別 | ^Delay/Damage/Lost/CustomsHold^
      発生場所（UN/LOCODE） | "USNYC"
      発生日時             | "2099-09-08 12:00"
      説明（任意）         | "通関手続きで荷物が滞留中"
    }
    {  : Lost 選択時のみ : <color:red>緊急フラグ自動 ON</color> }
    [キャンセル] | [記録]
  }
}
----------------
{+
  対応報告モーダル（新規、US19/US20）
  {+
    {
      対象例外     | "Delay (2099-09-08 12:00 @USNYC)"
      対応方針     | "代替ルート（VY-003）で再輸送、新到着予定 2099-09-15"
      解決日時     | "2099-09-09 09:00"
    }
    [キャンセル] | [解決済みとして記録]
  }
}
----------------
{+
  荷役登録（拡張、IT6 review M6 / US16 補正）
  {+
    {/ <b>CargoTracker</b> | ダッシュボード | 荷役管理 | [ログアウト] }
    {
      追跡番号 | "TN-000001"
      [(.) Receive  () Load  () Unload  () <b>Claim</b>]
      作業完了日時 | "2099-08-01 10:00"
      作業場所     | "USNYC"
      [Claim 時のみ表示]
      確認種別     | ^Signature/Stamp/IdCard/Code^
      確認内容     | "署名画像URL or コード"
    }
    [登録]
  }
}
@endsalt
```

#### 画面一覧（IT7 追加・拡張）

| 画面名 | URL | 説明 | アクセスロール | 関連 US |
|--------|-----|------|---------------|---------|
| 追跡詳細（拡張）| `/tracking/:trackingNumber` | 例外履歴セクション + 「例外を記録」「対応報告」ボタン（Tracker/MasterAdmin のみ）| Shipper, Tracker, MasterAdmin | **US19**, **US20** |
| 荷役登録（拡張）| `/handling/new` | Claim 時に確認種別セレクト追加（IT6 M6 補正）| Handler, Tracker | US16（補正） |
| 請求書発行（拡張）| `/billing/invoices/new` | 法人フラグ手入力廃止 + 料金内訳表示（IT6 H5/H6 解消）| Settlement, MasterAdmin | US21（補正） |
| 請求書詳細（拡張）| `/billing/invoices/:invoiceId` | 料金内訳 4 項目表示（基本料金 / 距離料金 / 重量料金 / 貨物種別料金）| Settlement, MasterAdmin | US21（補正） |

#### インタラクション

```plantuml
@startuml

title 画面遷移図（IT7 例外処理導線 + 補正画面）

[*] --> ログイン
state ログイン
ログイン --> ダッシュボード : ログイン成功（GET /）

state ダッシュボード
ダッシュボード --> 追跡詳細 : 「貨物追跡」→ 番号入力（GET /tracking/:n）
ダッシュボード --> 請求書一覧 : 「請求管理」（GET /billing/invoices）[Settlement]
ダッシュボード --> 荷役作業一覧 : 「荷役管理」（GET /handling）[Handler]

state 追跡詳細 : URL: /tracking/:trackingNumber
追跡詳細 --> 例外記録モーダル : 「例外を記録」[Tracker/MasterAdmin]（GET /exceptions/new、htmx hx-get）
追跡詳細 --> 対応報告モーダル : 「対応報告」（未解決例外行から、GET /exceptions/:id/resolve-form、htmx）
追跡詳細 --> 追跡詳細 : 30 秒 htmx ポーリング（IT5 既存）+ 例外履歴も含む

state 例外記録モーダル : URL: htmx 部分表示
例外記録モーダル --> 追跡詳細 : 「記録」成功（PRG: POST /tracking/:n/exceptions → /tracking/:n + alert-success + InException 遷移）
例外記録モーダル --> 例外記録モーダル : 種別/場所/日時バリデーション失敗（alert-danger、自己ループ）

state 対応報告モーダル : URL: htmx 部分表示
対応報告モーダル --> 追跡詳細 : 「解決済み」成功（PRG: POST /tracking/:n/exceptions/:eventId/resolve → /tracking/:n + alert-success）
対応報告モーダル --> 対応報告モーダル : 対応方針未入力 / 解決日時不正（alert-danger、自己ループ）

state 荷役作業登録 : URL: /handling/new
荷役作業登録 --> 荷役作業一覧 : Claim 登録成功（PRG、確認種別 + 内容両方必須）
荷役作業登録 --> 荷役作業登録 : Claim だが確認種別 or 内容欠落（alert-danger、自己ループ）

state 請求書発行補正 : URL: /billing/invoices/new
請求書発行補正 --> 請求書発行補正 : 予約 ID 入力時、Booking から法人/個人を自動判定（htmx hx-get /billing/invoices/preview）
請求書発行補正 --> 請求書詳細 : 「発行」成功（PRG）

ダッシュボード --> [*] : ログアウト
@enduml
```

#### htmx パターン

| パターン | 採用箇所 | 実装 |
|---------|---------|------|
| htmx モーダル取得 | 「例外を記録」「対応報告」 | `hx-get="/tracking/:n/exceptions/new" hx-target="#modal" hx-trigger="click"` で空フォーム取得、送信は通常 POST + PRG |
| htmx 部分更新 | 例外履歴セクション | 追跡タイムラインの 30 秒ポーリング (`hx-trigger="every 30s"`) に統合 |
| htmx エラー処理 | 楽観ロック競合（IT6 H8 補正） | `htmx:responseError` を listener で受け `alert-danger` を `#flash-area` に挿入、「再読込してください」表示 |
| htmx 自動判定 | 請求書発行画面の予約 ID 入力 | `hx-get="/billing/invoices/preview?bookingId=" hx-trigger="change delay:300ms"` で法人/個人 + 料金内訳 4 項目を取得 |
| 通常 POST + PRG | 例外記録 / 対応報告 / 荷役登録 / 請求書発行 | フォーム送信 → SEE_OTHER → 詳細・一覧画面に flash success/error |

#### フィードバックメッセージ

| トリガー | スタイル | メッセージ例 |
|---------|---------|------------|
| US19 遅延記録成功 | `alert-success` | 「例外（Delay）を記録しました。荷主に遅延通知を送信しました」 |
| US19 対応報告成功 | `alert-success` | 「対応報告を送信しました（新到着予定: 2099-09-15）」 |
| US20 紛失記録 + 緊急 | `alert-warning` | 「例外（Lost）を記録しました。<b>緊急フラグ ON</b> で管理職にエスカレーション通知を送信しました」 |
| US20 破損記録成功 | `alert-success` | 「例外（Damage）を記録しました。荷主に破損通知を送信しました」 |
| 時系列順序違反（記録日時 < 最終イベント時刻） | `alert-danger` | 「発生日時が直近の追跡イベントより過去です。日時を確認してください」 |
| 楽観ロック競合（IT6 H8 補正） | `alert-danger` | 「他のユーザーが先に更新しました。画面を再読み込みしてください」 |
| 荷受人確認種別欠落（IT6 M6 補正） | `alert-danger` | 「引取作業には確認種別（署名/受領印/身分証/コード）と内容の両方が必須です」 |
| 請求書発行で予約が法人荷主 | `alert-info`（情報表示）| 「法人荷主のため割引率 5.00% が自動適用されます」 |

### ディレクトリ構成

IT6 までの構成に対し、IT7 で以下を追加・変更する。

```text
apps/cargo-tracker/
├── app/
│   ├── cargotracker/
│   │   ├── booking/
│   │   │   ├── domain/model/
│   │   │   │   ├── aggregates/Cargo.scala               # IT7 拡張: Snapshot ADT + markException + resolveException
│   │   │   │   └── valueobjects/
│   │   │   │       ├── Itinerary.scala                  # IT7 拡張: legs に from/to UnLocode
│   │   │   │       └── ItineraryLeg.scala               # IT7 新規 (O3 解消)
│   │   │   ├── application/
│   │   │   │   ├── acl/
│   │   │   │   │   └── BookingCargoAclAdapter.scala     # IT7 新規 (Billing 側 Port の Booking 実装)
│   │   │   │   └── commandservices/
│   │   │   │       ├── BookingCommandService.scala      # IT7 拡張: logDelayNotification / escalateException / logExceptionResponded
│   │   │   │       └── HandlingOrchestrator.scala       # IT7 新規 (H3 解消)
│   │   │   └── infrastructure/repositories/
│   │   │       └── ScalikeJdbcCargoRepository.scala     # IT7 拡張: Snapshot 経由 reconstruct
│   │   ├── tracking/
│   │   │   ├── domain/model/
│   │   │   │   ├── aggregates/TrackingActivity.scala    # IT7 拡張: exceptions / addException / resolveException / hasActiveException
│   │   │   │   ├── entities/TrackingExceptionEvent.scala # IT7 新規
│   │   │   │   └── enums/ExceptionType.scala            # IT7 新規 (Delay/Damage/Lost/CustomsHold)
│   │   │   ├── application/commandservices/
│   │   │   │   └── TrackingCommandService.scala         # IT7 拡張: recordException / resolveException + OptimisticLock Either 化
│   │   │   └── infrastructure/repositories/
│   │   │       └── ScalikeJdbcTrackingExceptionEventRepository.scala  # IT7 新規
│   │   ├── handling/
│   │   │   ├── domain/model/
│   │   │   │   ├── aggregates/HandlingActivity.scala    # IT7 拡張: Snapshot + RegisterRequest, recipientConfirmationType
│   │   │   │   └── enums/RecipientConfirmationType.scala # IT7 新規
│   │   │   └── infrastructure/repositories/
│   │   │       └── ScalikeJdbcHandlingActivityRepository.scala  # IT7 拡張: Snapshot 経由 reconstruct
│   │   ├── billing/
│   │   │   ├── domain/model/
│   │   │   │   ├── aggregates/Invoice.scala             # IT7 拡張: Snapshot ADT
│   │   │   │   ├── valueobjects/
│   │   │   │   │   ├── InvoiceLineItem.scala            # IT7 新規 (料金内訳)
│   │   │   │   │   └── (Money.scala 削除 → shared.domain.Money に統合)
│   │   │   │   └── ports/BillingCargoQueryPort.scala    # IT7 新規 (H2 解消)
│   │   │   ├── application/commandservices/
│   │   │   │   └── BillingCommandService.scala          # IT7 拡張: Port 経由、法人フラグ自動判定、料金内訳生成
│   │   │   └── interfaces/web/
│   │   │       └── InvoiceController.scala              # IT7 拡張: 法人フラグ手入力削除
│   │   └── shared/
│   │       └── domain/
│   │           ├── Money.scala                          # IT7 拡張: multiplyByRate extension 追加
│   │           └── pricing/PricingService.scala         # IT7 拡張: calculateActual で InvoiceLineItem 返却
│   ├── views/
│   │   ├── tracking/detail.scala.html                   # IT7 拡張: 例外履歴 + 記録/対応モーダル + Tracker ロール限定 + 手動更新理由
│   │   ├── handling/newForm.scala.html                  # IT7 拡張: recipient_confirmation_type セレクト
│   │   └── billing/
│   │       ├── newForm.scala.html                       # IT7 拡張: 法人フラグ手入力削除、htmx 自動判定
│   │       └── detail.scala.html                        # IT7 拡張: 料金内訳 4 項目
│   └── test/
│       ├── cargotracker/arch/
│       │   └── HexagonalArchitectureSpec.scala          # IT7 拡張: contexts に billing/handling/tracking/notification 追加 (H1)
│       └── cargotracker/billing/infrastructure/
│           └── ScalikeJdbcInvoiceRepositoryIntegrationSpec.scala  # IT7 新規 (M9 楽観ロック IT)
├── conf/
│   ├── routes                                           # IT7 拡張: 4 エンドポイント追加 (例外記録/対応報告/プレビュー)
│   └── db/migration/default/
│       ├── V18__handling_recipient_confirmation_type.sql # IT7 新規
│       ├── V19__cargo_itinerary_leg_from_to.sql         # IT7 新規
│       ├── V20__create_tracking_exception_event.sql     # IT7 新規
│       └── V21__notification_log_check_exceptions.sql   # IT7 新規
└── docs/adr/
    ├── 0014-aggregate-snapshot-adt.md                   # IT7 で承認
    ├── 0015-billing-single-currency-jpy.md              # IT7 0.4 で新規
    └── 0016-cross-context-orchestrator-pattern.md       # IT7 0.3 で新規 (候補)
```

### API 設計

| メソッド | エンドポイント | 説明 | 関連 US | 認証 |
|---------|---------------|------|---------|------|
| POST | `/tracking/:trackingNumber/exceptions` | 例外記録（PRG）。種別 / 場所 / 日時 / description / (Lost 時) escalationFlag 自動 ON | US19/US20 | Tracker/MasterAdmin |
| GET | `/tracking/:trackingNumber/exceptions/new` | 例外記録モーダル取得（htmx） | US19/US20 | Tracker/MasterAdmin |
| POST | `/tracking/:trackingNumber/exceptions/:eventId/resolve` | 対応報告（PRG）。resolved_at + resolution_notes 永続化 + Cargo.resolveException 連動 | US19/US20 | Tracker/MasterAdmin |
| GET | `/tracking/:trackingNumber/exceptions/:eventId/resolve-form` | 対応報告モーダル取得（htmx） | US19/US20 | Tracker/MasterAdmin |
| GET | `/billing/invoices/preview` | 料金内訳 + 法人/個人判定 htmx プレビュー（IT6 H5/H6 補正） | US21 補正 | Settlement |
| POST | `/handling` | 荷役登録（recipient_confirmation_type 必須化 IT6 M6 補正） | US16 補正 | Handler |

### ADR

| ADR | タイトル | ステータス | 関連タスク |
|-----|---------|-----------|------|
| [ADR 0014](../adr/0014-aggregate-snapshot-adt.md) | 集約 reconstruct / register に Snapshot ADT を導入し SonarQube MAJOR Code Smell 4 件を解消 | 提案 → **IT7 で承認**（0.16 で確認）| 0.5, 0.6, 0.7 |
| ADR 0015 | Billing は単通貨 JPY、`shared.domain.Money` 一本化（IT6 H4 解消）| 0.4 で起票 | 0.4 |
| ADR 0016（候補）| コンテキスト間 Orchestrator パターン（`HandlingOrchestrator` 経由で単一 DB.localTx 境界）| 0.3 で起案検討 | 0.3 |
| ADR 0017（候補）| ArchUnit contexts ルール拡張ガイドライン（IT7 0.1 の知見を将来コンテキスト追加時に再利用）| 0.1 完了後に検討 | 0.1 |

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| Snapshot ADT 適用時に既存テストが大量破壊される | 中 | 各集約ごとに 1 コミット単位で進め、テスト更新→緑→次集約のリズム維持。`@deprecated` 旧 API を一時並存 |
| HandlingOrchestrator 抽出で Controller 経由フローが壊れる | 高 | E2E 36 件を回帰テストとして必ず実行、Orchestrator は既存 4 操作の順序を保ったまま単一 localTx に包む |
| `cargo_itinerary_leg` 追加で既存 cargo データの整合性問題 | 中 | NULL 許容で導入し、新規予約のみ leg を持つ。既存予約は `Itinerary.voyageNumbers` から best-effort で生成 |
| US19/US20 の例外処理が複雑化し SP 超過 | 高 | 受け入れ条件最小実装で着地、緊急フラグ詳細制御は IT8 へ申し送り可 |
| ArchUnit 拡張で既存違反が大量検出される | 中 | 0.1 で違反一覧を取得し、0.2/0.3 で構造修正、残りは ADR で許容範囲を明文化 |

---

## 完了条件

### Definition of Done

- [ ] コードレビュー完了（self-review）
- [ ] Unit テスト全件 PASS（261+ 件 + 例外関連 10 件以上追加）
- [ ] Playwright E2E 全件 PASS（36+ 件 + US19/US20 で 3 件以上追加）
- [ ] Testcontainers IT 全件 PASS（Invoice 楽観ロック IT 追加で 3 件以上）
- [ ] scalafmt / scalafix 緑
- [ ] ArchUnit 5/5 緑（新コンテキスト 4 つ追加後）
- [ ] SonarQube Quality Gate ✅ OK / MAJOR Code Smell 0 件 / Coverage 80% 以上
- [ ] Flyway V18-V21 適用済
- [ ] ADR 0014 承認 + ADR 0015 起票
- [ ] developing-review 正式実施（XP 5 エージェント並列）

### デモ項目

1. **アーキ堅牢化**: ArchUnit で 9 コンテキスト全てが境界検査対象、HandlingOrchestrator 経由で Claim 登録時の単一 DB.localTx 動作
2. **Snapshot ADT**: Invoice / Cargo / HandlingActivity の `reconstruct(snapshot)` API デモ、SonarQube MAJOR 0 件
3. **業務適合性**: 法人荷主の予約から請求書発行 → 自動で割引率反映 → 料金内訳 (基本料金 / 重量料金 / 距離料金 / 貨物種別料金) 表示
4. **US19 遅延例外**: 追跡詳細 → 例外記録 → InException 遷移 → 荷主通知 → 対応報告
5. **US20 破損・紛失例外**: 紛失記録 → 緊急フラグ → 管理職 escalation 通知 → 補償方針入力

---

## 関連ドキュメント

- [IT6 ふりかえり](./retrospective-6.md)
- [IT6 完了報告書](./iteration_report-6.md)
- [IT6 実装レビュー (developing-review)](../review/it6_implementation_review_20260623.md)
- [ADR 0014 集約 Snapshot ADT 導入](../adr/0014-aggregate-snapshot-adt.md)
- [リリース計画](./release_plan.md)

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-06-23 | IT7 計画策定（US19 + US20 + IT6 申し送り 16 件、Phase 4 着手、Release 2.0 GA 基盤整備） | AI Agent |
