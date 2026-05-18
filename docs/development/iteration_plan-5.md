---
title: イテレーション 5 計画
description: IT5（荷役作業記録・追跡基盤）の詳細計画。handlingms 新設・US15/US16/US17 実装・11 SP。
---

# イテレーション 5 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 5 |
| **期間** | Week 9-10（2026-07-09 〜 2026-07-22） |
| **ゴール** | `handlingms` を新設し、荷役作業記録（US15/US16）と貨物状態手動更新（US17）を実装することで Phase 2 追跡基盤を確立する |
| **目標 SP** | 11（新規 11 SP） |
| **基準ベロシティ** | 14.7 SP（IT1〜IT3 平均）／ IT4 は特例スコープのため除外 |

---

## ゴール

### イテレーション終了時の達成状態

1. **handlingms 稼働**: Axon Event Sourcing による `HandlingActivity` Aggregate が起動し、荷役作業コマンドを受け付ける
2. **荷役作業記録（US15/US16）**: 追跡番号を使って受領・積込・荷降し・引取の 4 作業種別が記録でき、貨物状態に反映される
3. **状態手動更新（US17）**: 追跡管理者が追跡番号を指定して貨物状態・位置を手動更新でき、イベント履歴に追記される

### 成功基準

- [ ] `POST /api/v1/handling/activities` が作業記録を受け付けて `HandlingActivityRegisteredEvent` を発行する
- [ ] `PUT /api/v1/handling/activities/{trackingNumber}/status` が状態手動更新を実行する
- [ ] handlingms の全ユニット/統合テストがパス
- [ ] フロントエンドの荷役作業記録フォーム（S20）・状態更新フォーム（S17 内モーダル）が動作する
- [ ] Playwright E2E テストが追加・全通過する
- [ ] SonarQube Quality Gate PASS（new_coverage ≥ 80%・violations 0）
- [ ] IT4 コードレビュー指摘の高優先度 3 件を対応済みにする（H1 ArchUnit・H2 タイムアウト・H3 テスト更新）

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 | 区分 |
|----|------------------|----|----|------|
| TI04 | IT5 第 0 スプリント（handlingms 骨格・ADR-0012・IT4 レビュー H1-H3 対応） | 2 | 必須 | 技術タスク |
| US15 | 荷役作業を記録する | 5 | 必須 | 新規 |
| US16 | 引取作業を記録する | 2 | 必須 | 新規（US15 拡張） |
| US17 | 貨物状態を手動更新する | 2 | 必須 | 新規 |
| **合計** | | **11** | | |

> **フィーチャバッファ**: US16 引取確認フィールド（1 SP）は US15 が先行完了した場合に実装。US17 通知送信（1 SP）は IT6 以降に繰越し可能。

### ストーリー詳細

#### TI04: IT5 第 0 スプリント

**目的**: handlingms の Axon Event Sourcing 骨格を確立し、IT4 コードレビュー指摘（H1〜H3）を解消する。

**受入条件**:

1. handlingms に `HandlingActivity` Aggregate クラスが存在し Spring Boot が起動できる
2. ADR-0012「handlingms と trackingms の責務分離方針」が承認済み
3. ArchUnit で `@TargetEntityId` 欠落コマンドを検出するアーキテクチャテストが bookingms に追加されている
4. `sendAndWait()` のタイムアウトが `BookingController` 全 3 箇所に明示指定されている（例: 30 秒）
5. `confirm`・`issue-tracking` の統合テストが `sendAndWait` に更新されている

#### US15: 荷役作業を記録する

**ストーリー**:
> 荷役作業員として、追跡番号を入力して貨物を特定し、作業種別・日時・場所を登録したい。なぜなら、荷役作業完了が即座に貨物状態に反映され、荷主がリアルタイムで確認できるからだ。

**受入条件**:

1. 追跡番号の入力（またはスキャン）で貨物を特定できる
2. 作業種別（受領・積込・荷降し）を選択できる
3. 作業日時と作業場所（UN/LOCODE 形式の港湾コード）を入力できる
4. 記録後、貨物状態が対応する状態（受領済・積込済・荷降し済）に自動更新される
5. 記録後、荷主に状態変更通知が送信される（IT5 はログのみ、実送信は IT6+）
6. 追跡番号が存在しない場合、エラーメッセージが表示される
7. 作業場所が予定ルートと異なる場合、警告が表示される

#### US16: 引取作業を記録する

**ストーリー**:
> 荷役作業員として、荷受人が貨物を引き取る際に、荷受人の確認（署名または確認コード）を取得して引取作業を記録したい。なぜなら、荷受人への正式な引き渡しを証明し、配送完了を記録できるからだ。

**受入条件**:

1. 作業種別「引取」を選択すると、荷受人確認フィールド（署名または確認コード）が表示される
2. 荷受人確認が取得されると引取作業が記録される
3. 記録後、貨物状態が「引取済」に更新される
4. 貨物状態「引取済」は配送完了を意味し、精算処理の開始条件となる

#### US17: 貨物状態を手動更新する

**ストーリー**:
> 追跡管理者として、追跡番号を指定して貨物の状態・位置・更新日時を手動で更新したい。なぜなら、荷役作業員の記録だけでは捕捉できない状態変化（出港・入港等）を追跡情報に反映できるからだ。

**受入条件**:

1. 追跡番号を指定して現在の貨物情報を確認できる
2. 新しい状態・位置・日時を入力して追跡情報を更新できる
3. 更新後、追跡イベントが履歴に記録される
4. 状態変更の種類に応じて荷主への通知が送信される（IT5 はログのみ、実送信は IT6+）

---

## タスク

### 1. TI04: IT5 第 0 スプリント（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | ADR-0012 起票（handlingms / trackingms 責務分離・Saga 方針） | 2h | - | [ ] |
| 1.2 | handlingms 骨格作成（Spring Boot + Axon + MyBatis + Flyway 設定） | 3h | - | [ ] |
| 1.3 | ArchUnit テスト追加（`@TargetEntityId` 強制） | 2h | - | [ ] |
| 1.4 | `BookingController` `sendAndWait` タイムアウト明示指定（30s） | 1h | - | [ ] |
| 1.5 | `BookingControllerIntegrationTest` confirm/issue-tracking を sendAndWait に更新 | 1h | - | [ ] |
| 1.6 | gatewayms に `/api/v1/handling/**` ルーティング追加 | 1h | - | [ ] |

**小計**: 11h

### 2. US15: 荷役作業を記録する（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | `HandlingActivity` Aggregate（受領・積込・荷降し コマンド/イベント定義） | 3h | - | [ ] |
| 2.2 | `RecordHandlingActivityCommand` + `HandlingActivityRecordedEvent` | 2h | - | [ ] |
| 2.3 | `HandlingProjectionsEventHandler`（handling_activity テーブル投影） | 2h | - | [ ] |
| 2.4 | `HandlingController` POST `/api/v1/handling/activities` | 2h | - | [ ] |
| 2.5 | Flyway V001（handlingms）: `handling_activity` テーブル作成 | 1h | - | [ ] |
| 2.6 | ユニットテスト（Aggregate）+ 統合テスト（REST） | 3h | - | [ ] |
| 2.7 | フロントエンド S15 荷役作業記録フォーム | 3h | - | [ ] |

**小計**: 16h

### 3. US16: 引取作業を記録する（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | 引取コマンド（`RecordPickupCommand`）と確認コードフィールド追加 | 2h | - | [ ] |
| 3.2 | 「引取済」状態遷移テスト | 2h | - | [ ] |
| 3.3 | フロントエンド 引取確認フィールド表示条件 | 1h | - | [ ] |

**小計**: 5h

### 4. US17: 貨物状態を手動更新する（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 4.1 | `ManualStatusUpdateCommand` + `StatusManuallyUpdatedEvent` | 2h | - | [ ] |
| 4.2 | `HandlingController` PUT `/api/v1/handling/activities/{trackingNumber}/status` | 2h | - | [ ] |
| 4.3 | ユニットテスト + 統合テスト | 2h | - | [ ] |
| 4.4 | フロントエンド S17 状態手動更新フォーム | 2h | - | [ ] |

**小計**: 8h

### 5. E2E テスト + SonarQube（バッファ）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 5.1 | Playwright E2E: US15/US17 フルフロー | 3h | - | [ ] |
| 5.2 | SonarQube スキャン・violations 修正 | 2h | - | [ ] |

**小計**: 5h

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|----|------|
| TI04 第 0 スプリント | 2 | 11h | [ ] |
| US15 荷役作業記録 | 5 | 16h | [ ] |
| US16 引取作業記録 | 2 | 5h | [ ] |
| US17 状態手動更新 | 2 | 8h | [ ] |
| E2E・品質 | — | 5h | [ ] |
| **合計** | **11** | **45h** | |

**1 SP あたり**: 約 4h（実装 + テスト）
**進捗率**: 0%（0/11 SP）

---

## スケジュール

### Week 1（Day 1-5）: 骨格確立・US15 実装

```mermaid
gantt
    title イテレーション 5 - Week 1
    dateFormat  YYYY-MM-DD
    section 第 0 スプリント
    ADR-0012・handlingms 骨格   :d1, 2026-07-09, 1d
    ArchUnit・タイムアウト・ゲートウェイ :d2, after d1, 1d
    section US15
    Aggregate + コマンド/イベント   :d3, after d2, 1d
    Projection + REST              :d4, after d3, 1d
    フロントエンド S15              :d5, after d4, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | ADR-0012 起票・handlingms 骨格作成・gateway 追加 |
| Day 2 | ArchUnit テスト・sendAndWait タイムアウト・統合テスト更新 |
| Day 3 | HandlingActivity Aggregate + RecordHandlingActivityCommand/Event |
| Day 4 | HandlingProjectionsEventHandler + HandlingController + Flyway V001 |
| Day 5 | フロントエンド S15 荷役作業記録フォーム |

### Week 2（Day 6-10）: US16/US17・E2E・品質

```mermaid
gantt
    title イテレーション 5 - Week 2
    dateFormat  YYYY-MM-DD
    section US16
    引取コマンド + テスト + UI   :a1, 2026-07-16, 1d
    section US17
    手動更新コマンド + REST + UI :a2, after a1, 2d
    section 品質
    E2E テスト                  :a3, after a2, 1d
    SonarQube・最終確認         :a4, after a3, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | US16 引取コマンド・確認フィールド UI |
| Day 7 | US17 ManualStatusUpdateCommand・PUT エンドポイント |
| Day 8 | US17 フロントエンド S17・統合テスト |
| Day 9 | Playwright E2E US15/US17 フルフロー |
| Day 10 | SonarQube スキャン・violations 修正・デモ準備 |

---

## 設計

### ドメインモデル

```plantuml
@startuml
title Handling Context（IT5 実装スコープ）

class HandlingActivity <<Aggregate Root>> {
  - activityId: HandlingActivityId
  - cargoSnapshot: CargoSnapshot
  - handlingType: HandlingType
  - occurredAt: LocalDateTime
  - location: Location
  - voyageNumber: VoyageNumber (optional)
  - operatorId: HandlerId
  - claimVerification: ClaimVerification (optional)
  + handle(RegisterHandlingActivityCommand)
  + isValidFor(snapshot: CargoSnapshot, type: HandlingType): boolean
}

class CargoSnapshot <<ACL>> <<Value Object>> {
  - bookingId: BookingId
  - trackingNumber: TrackingNumber
  - origin: Location
  - destination: Location
  - cargoType: CargoType
  - itinerarySnapshot: CargoItinerary
  + isExpectedHandling(type: HandlingType, loc: Location): boolean
}

class ClaimVerification <<Value Object>> {
  - consigneeName: String
  - signatureRef: String (optional)
  - confirmationCode: String (optional)
  - verifiedAt: LocalDateTime
}

enum HandlingType {
  RECEIVE
  LOAD
  UNLOAD
  CLAIM
  CUSTOMS
}

HandlingActivity *-- CargoSnapshot
HandlingActivity *-- HandlingType
HandlingActivity *-- "0..1" ClaimVerification
HandlingActivity ..> HandlingActivityRegisteredEvent
HandlingActivity ..> UnexpectedHandlingDetectedEvent

note bottom of CargoSnapshot
  ACL（腐敗防止層）
  Booking Context の Cargo に直接依存せず、
  CargoBookedEvent / CargoRoutedEvent を購読して
  独自モデルに変換して保持する。
end note
@enduml
```

> **ドメインモデルの注意点**:
> - `HandlingType` の enum 名は `HandlingActivityType` ではなく `HandlingType`（domain-model.md 準拠）
> - `PICKUP` は `CLAIM`（引取）が正しい用語。`MANUAL_UPDATE` は `HandlingType` ではなく US17 は `trackingms` 責務（IT5 では `handlingms` 内の projection で暫定対応）
> - `trackingNumber` は直接フィールドではなく `CargoSnapshot` に内包
> - `claimVerification` は `String` ではなく値オブジェクト `ClaimVerification`（`CLAIM` 時のみ必須）
> - `voyageNumber` は `LOAD`/`UNLOAD` 時に必須

### データモデル

```plantuml
@startuml
hide circle
skinparam linetype ortho

entity "handling_activity" as ha {
  * **activity_id**: VARCHAR(36) <<PK>>
  --
  ' CargoSnapshot ACL の射影
  booking_id: VARCHAR(36) NOT NULL
  tracking_number: VARCHAR(20) NOT NULL
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  cargo_type: VARCHAR(16) NOT NULL
  ' 荷役作業本体
  handling_type: VARCHAR(16) NOT NULL
  occurred_at: TIMESTAMPTZ NOT NULL
  recorded_at: TIMESTAMPTZ NOT NULL
  unlocode: VARCHAR(5) NOT NULL
  voyage_number: VARCHAR(20)
  handler_id: VARCHAR(36) NOT NULL
  ' フラグ
  unexpected: BOOLEAN NOT NULL DEFAULT FALSE
  version: BIGINT
}

entity "handling_itinerary_snapshot" as his {
  * **activity_id**: VARCHAR(36) <<PK>> <<FK>>
  * **leg_seq**: INTEGER <<PK>>
  --
  voyage_number: VARCHAR(20) NOT NULL
  load_unlocode: VARCHAR(5) NOT NULL
  unload_unlocode: VARCHAR(5) NOT NULL
  load_at: TIMESTAMPTZ NOT NULL
  unload_at: TIMESTAMPTZ NOT NULL
}

entity "claim_verification" as cv {
  * **activity_id**: VARCHAR(36) <<PK>> <<FK>>
  --
  consignee_name: VARCHAR(200) NOT NULL
  signature_ref: VARCHAR(200)
  confirmation_code: VARCHAR(50)
  verified_at: TIMESTAMPTZ NOT NULL
}

ha ||--o{ his : "0..*"
ha ||--o| cv : "0..1（CLAIM 時のみ）"
@enduml
```

> **データモデルの注意点（data-model.md 準拠）**:
> - カラム名: `activity_type` → `handling_type`、`location_unlocode` → `unlocode`、`activity_datetime` → `occurred_at` + `recorded_at`
> - `recipient_confirmation` は `claim_verification` テーブルに分離（`CLAIM` 種別時のみ）
> - CargoSnapshot 情報（`booking_id`・`origin_unlocode`・`destination_unlocode`・`cargo_type`）は Read Model に射影
> - `handler_id`・`unexpected`・`voyage_number` は必須カラム
> - Flyway V001 では `handling_activity`・`handling_itinerary_snapshot`・`claim_verification` の 3 テーブルを作成

### API 設計

| メソッド | エンドポイント | 説明 | ロール |
|---------|---------------|------|--------|
| `POST` | `/api/v1/handling/activities` | 荷役作業を記録する | `ROLE_HANDLER` |
| `PUT` | `/api/v1/handling/activities/{trackingNumber}/status` | 貨物状態を手動更新する | `ROLE_TRACKER` |
| `GET` | `/api/v1/handling/activities/{trackingNumber}` | 作業履歴を照会する | `ROLE_HANDLER`, `ROLE_TRACKER` |

#### POST /api/v1/handling/activities リクエスト例

```json
{
  "trackingNumber": "TRK-20260718-ABC12345",
  "activityType": "RECEIVE",
  "location": "JPTYO",
  "activityDateTime": "2026-07-18T09:00:00",
  "recipientConfirmation": null
}
```

### ADR

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| ADR-0012（新規） | handlingms と trackingms の責務分離・Saga 方針 | 提案 |

> **ADR-0012 要点**: US15〜US17 は `handlingms` で実装し `HandlingActivityRegisteredEvent` を発行。将来の追跡照会（US18）は `trackingms` が当該イベントをサブスクライブして Read Model を構築する。IT5 では `trackingms` は作成せず、handlingms 内の projection で代替する。
>
> **Saga 方針（IT4 コードレビュー H4 対応）**: bookingms の `sendAndWait()` 連鎖（booking → routing → handling）が増加した場合、Axon Saga または ProcessManager への移行を検討する。IT5 時点では handlingms のコマンド受付は単一 Aggregate 操作のため Saga 化は不要。IT6 以降で bookingms ↔ handlingms 連携が発生した時点で方針を確定する。

### ディレクトリ構成（新規）

```
apps/backend/handlingms/
├── src/main/java/com/example/cargotracker/handlingms/
│   ├── HandlingApplication.java
│   ├── domain/model/
│   │   ├── aggregates/HandlingActivity.java
│   │   ├── commands/RecordHandlingActivityCommand.java
│   │   ├── commands/RecordPickupCommand.java
│   │   ├── commands/ManualStatusUpdateCommand.java
│   │   └── events/HandlingActivityRecordedEvent.java
│   ├── infrastructure/persistence/
│   │   ├── HandlingActivityMapper.java
│   │   └── HandlingActivityRecord.java
│   └── interfaces/rest/
│       ├── HandlingController.java
│       └── dto/RecordActivityRequest.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/V001__create_handling_activity.sql
```

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| handlingms 新設の設定コスト超過 | 中 | 第 0 スプリント（Day 1-2）で骨格を確立してから機能実装に移行。bookingms の設定を参考にして短縮 |
| Axon コマンドルーティング再発（@TargetEntityId 欠落） | 高 | TI04 で ArchUnit テストを追加。CI で自動検出 |
| `sendAndWait()` タイムアウト超過 | 中 | タイムアウト 30 秒を明示指定。異常系レスポンスを統合テストで検証 |
| US16 引取確認フィールドの業務未合意 | 低 | 確認コード形式（UUID / 署名画像 / 4 桁 PIN）を IT5 計画前にユーザー代表と合意 |

---

## 完了条件

### Definition of Done

- [ ] コードレビュー（`developing-review`）完了、または SonarQube Quality Gate PASS で代替
- [ ] 全ユニットテストがパス（バックエンド・フロントエンド）
- [ ] Playwright E2E テストがパス（US15/US17 フルフロー含む）
- [ ] SonarQube Quality Gate PASS（new_coverage ≥ 80%・new_violations 0）
- [ ] ArchUnit テストがパス（`@TargetEntityId` 強制）
- [ ] ADR-0012 承認済み
- [ ] gateway に `/api/v1/handling/**` が登録済み
- [ ] IT4 コードレビュー指摘 H1〜H3 対応済み

### デモ項目

1. 荷役作業記録フォーム（S20）で追跡番号を入力し「受領」作業を記録 → 成功
2. 「引取（CLAIM）」を選択すると荷受人確認フィールド（署名または確認コード）が表示される
3. 追跡番号を指定して貨物状態を「積込済」に手動更新 → 履歴に追記される

### IT6 繰越し事項（IT5 スコープ外）

以下は IT4 コードレビュー中優先度指摘（M1〜M6）の対応。IT5 では実装せず IT6 計画に引き継ぐ。

| ID | 内容 | 指摘元 |
|----|------|--------|
| M1 | `data-testid` 属性を UI 要素に付与（E2E ロケーター強化） | xp-programmer / xp-tester |
| M2 | gatewayms predicates を YAML リスト形式に変更 | xp-programmer |
| M3 | Tracking Number フォーマット仕様を ADR に記録 | xp-architect |
| M4 | `sendAndWait` 変更理由を Javadoc に追記 | xp-technical-writer |
| M5 | `NotifyRouteCommand` に IT5+ メール送信予定を記載 | xp-technical-writer |
| M6 | `sendAndWait` 遅延時の処理中インジケータ追加（二重送信防止） | xp-user-representative |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-18 | 初版作成（IT4 完了後・IT4 コードレビュー指摘反映） | AI Agent（XP PM） |
| 2026-05-18 | 整合性検証対応: US15 受入条件 7 件に修正、US16/US17 ストーリー文を user_story.md 準拠に修正、ドメインモデルを domain-model.md 準拠（HandlingType・CargoSnapshot・ClaimVerification）に修正、データモデルを data-model.md 準拠（handling_type・occurred_at・claim_verification テーブル分離等）に修正、TI04 見積もり 11h に修正、ADR-0012 に Saga 方針（H4）追記、IT6 繰越し事項（M1〜M6）追記 | AI Agent |

---

## 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 4 計画](./iteration_plan-4.md)
- [イテレーション 4 完了報告書](./iteration_report-4.md)
- [IT4 バグ修正コードレビュー](../review/IT4_bugfix_review_20260518.md)
