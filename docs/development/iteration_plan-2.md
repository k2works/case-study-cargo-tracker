# イテレーション 2 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 |
| **期間** | Week 3-4（2026-05-12〜2026-05-23） |
| **ゴール** | 貨物予約登録 API + 画面、および経路候補算出 API + 経路設計画面を構築する |
| **目標 SP** | 24（BE 16 + FE 8） |

---

## ゴール

### イテレーション終了時の達成状態

1. **貨物予約登録**: bookingms で貨物予約（荷主・出発地・目的地・希望期限・貨物種別・重量）を登録できる REST API が動作し、React SPA で予約一覧・登録画面が実装されている
2. **経路候補算出**: routingms で出発地・目的地・希望期限を入力として経路候補（航海スケジュールの組み合わせ）を算出できる REST API が動作し、React SPA の経路設計画面で候補が表示される
3. **フルスタック連携**: bookingms と routingms が Gateway 経由で連携し、予約から経路候補表示までの一連のフローが動作する

### 成功基準

- [ ] US04: 貨物予約の新規登録 API が動作する（認証必須）
- [ ] US04: 貨物予約一覧・登録画面が動作する
- [ ] US08: 経路候補算出 API が動作する（認証必須）
- [ ] US08: 経路設計画面で候補が表示される
- [ ] ArchUnit テストが通過する（ヘキサゴナル依存ルール）
- [ ] テストカバレッジ 80% 以上（bookingms + routingms、JaCoCo / Vitest で測定）

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | BE | FE | SP | 優先度 |
|----|-------------------|----|----|----|----|
| US04 | 貨物予約を登録する | 8 | 5 | 13 | 必須 |
| US08 | 経路候補を算出する | 8 | 3 | 11 | 必須 |
| **合計** | | **16** | **8** | **24** | |

### ストーリー詳細

#### US04: 貨物予約を登録する

**ストーリー**:

> 営業担当者として、荷主・貨物仕様（種別・重量・品名）・輸送条件（出発地・目的地・希望期限）を入力して予約を登録したい。なぜなら、荷主の見積承認後に正式な予約を受け付け、経路設計フェーズに引き継げるからだ。

**受入条件（IT2 実装スコープ）**:

1. 荷主 ID・貨物種別（GENERAL / HAZARDOUS / REFRIGERATED）・重量・出発地（UN/LOCODE）・目的地（UN/LOCODE）・希望到着期限を入力できる
2. 登録完了後、予約番号（BK-XXXX 形式）が発行され状態が `PRELIMINARY` になる
3. 重複や不正データがある場合、422 エラーとエラーメッセージを返す
4. 認証なしのリクエストは 401 エラーを返す

> **IT3 以降に持ち越し**: 寸法・個数・品名・荷受人情報の入力、経路設計者への通知（ドメインイベント）、見積情報との整合性確認、危険物申告・温度管理フィールド（US05）

#### US08: 経路候補を算出する

**ストーリー**:

> 経路設計者として、貨物予約の出発地・目的地・希望期限をもとに経路候補を自動算出してほしい。なぜなら、手作業の属人化を解消し、接続可能な航海スケジュールを組み合わせた最適経路を効率的に見つけられるからだ。

**受入条件（IT2 実装スコープ）**:

1. 出発地・目的地・希望到着期限を入力として経路候補が算出される
2. 期限内に到達可能な航海スケジュールの組み合わせが候補として提示される
3. 経路候補ごとに所要日数・経由港・航海番号が表示される
4. 直行便がある場合は最優先候補として提示される
5. 期限内に到達可能な経路がない場合、空リストを返す
6. 経路候補は推奨順（直行便優先 → 所要日数の短い順）に並べられる

> **IT3 以降に持ち越し**: 費用（estimated_cost）の表示、条件調整 UI（期限内経路なし時の再入力フロー）

---

## タスク

### Task 1: bookingms TDD 基盤構築（2 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 1.1 | Flyway マイグレーション（cargo テーブル） | 1h | [ ] |
| 1.2 | Cargo 集約ドメインモデル（BookingId / ShipperId / RouteSpecification / CargoType / Weight / BookingStatus）※IT2スコープ | 2h | [ ] |
| 1.3 | BookingStatus 全状態値を定義（PRELIMINARY / ROUTE_PROPOSED / CONFIRMED / TRACKING_ISSUED / IN_TRANSIT / DELIVERED / SETTLED / CANCELLED） | 1h | [ ] |
| 1.4 | CargoMapper（MyBatis）+ MyBatisCargoRepository | 2h | [ ] |
| 1.5 | ArchUnit テスト（ヘキサゴナル依存ルール） | 1h | [ ] |

**小計**: 7h

### Task 2: US04 BE — 貨物予約 API（8 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 2.1 | CargoCommandService（予約登録）TDD | 2h | [ ] |
| 2.2 | CargoQueryService（一覧・詳細）TDD | 2h | [ ] |
| 2.3 | CargoController（POST /api/booking/v1/cargos）+ DTO | 2h | [ ] |
| 2.4 | CargoController（GET /api/booking/v1/cargos, GET /:bookingId）| 2h | [ ] |
| 2.5 | Gateway ルート追加（/api/booking/**）| 1h | [ ] |
| 2.6 | MockMvc 統合テスト | 2h | [ ] |

**小計**: 11h

### Task 3: US04 FE — 貨物予約画面（5 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 3.1 | Cargo 型定義 + useBookings / useCreateBooking hooks | 1h | [ ] |
| 3.2 | BookingList コンポーネント（ステータスバッジ付きテーブル） | 2h | [ ] |
| 3.3 | BookingForm コンポーネント（React Hook Form + バリデーション） | 2h | [ ] |
| 3.4 | BookingListPage / BookingNewPage / App.tsx ルート追加 | 1h | [ ] |
| 3.5 | ナビゲーションに「貨物予約」追加（ROLE_SALES） | 0.5h | [ ] |
| 3.6 | Vitest コンポーネントテスト | 1.5h | [ ] |

**小計**: 8h

### Task 4: US08 BE — 経路候補算出 API（8 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 4.1 | RoutingSpec 値オブジェクト（origin: String / destination: String / arrivalDeadline: LocalDate） | 1h | [ ] |
| 4.2 | Itinerary / Leg ドメインモデル（Leg: voyage: VoyageNumber / loadLocation / unloadLocation / loadTime / unloadTime） | 2h | [ ] |
| 4.3 | RouteFinderService TDD（接続可能航海の組み合わせ算出・直行便優先→所要日数昇順ソート） | 3h | [ ] |
| 4.4 | RoutingController（POST /api/routing/v1/itineraries） + DTO（ItineraryResponse: legs / totalDays） | 2h | [ ] |
| 4.5 | MockMvc 統合テスト（正常系: 直行便 / 乗継 / 経路なし） | 2h | [ ] |

**小計**: 10h

### Task 5: US08 FE — 経路設計画面（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 5.1 | Itinerary 型定義 + useItineraries hook | 1h | [ ] |
| 5.2 | RoutingDesignPage（経路候補一覧・選択 UI） | 2h | [ ] |
| 5.3 | App.tsx ルート追加（/routing/design/:bookingId） | 0.5h | [ ] |
| 5.4 | Vitest コンポーネントテスト | 1.5h | [ ] |

**小計**: 5h

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|----|------|
| Task 1: bookingms 基盤構築 | 2 | 7h | [ ] |
| Task 2: US04 BE 貨物予約 API | 8 | 11h | [ ] |
| Task 3: US04 FE 貨物予約画面 | 5 | 8h | [ ] |
| Task 4: US08 BE 経路候補算出 API | 8 | 10h | [ ] |
| Task 5: US08 FE 経路設計画面 | 3 | 5h | [ ] |
| **合計** | **26** | **41h** | |

**1 SP あたり**: 約 1.6h（IT1 実績 2.1h より効率改善を期待）

**進捗率**: 0% (0/24 SP)

> **Note**: IT1 のふりかえり「Try」を踏まえ、API パスを事前に Gateway ルートと照合済み（`/api/booking/**`）。FE 実装時は `docs/design/ui-design.md` のワイヤーフレームを必ず参照する。

---

## スケジュール

### Week 1（Day 1-5: 2026-05-12〜2026-05-16）— bookingms 基盤 + 予約 API

```mermaid
gantt
    title イテレーション 2 - Week 1（bookingms 基盤 + 予約 API）
    dateFormat  YYYY-MM-DD
    section BE bookingms
    DB + ドメインモデル + ArchUnit  :d1, 2026-05-12, 1d
    CargoCommandService TDD         :d2, after d1, 1d
    CargoQueryService TDD           :d3, after d2, 1d
    CargoController + Gateway       :d4, after d3, 1d
    統合テスト + FE 貨物予約画面    :d5, after d4, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | Task 1（bookingms 基盤: DB + ドメインモデル + ArchUnit） |
| Day 2 | Task 2.1-2.2（CargoCommandService + CargoQueryService TDD） |
| Day 3 | Task 2.3-2.4（CargoController + DTO） |
| Day 4 | Task 2.5-2.6（Gateway ルート + 統合テスト） |
| Day 5 | Task 3（FE: BookingList + BookingForm + ページ） |

### Week 2（Day 6-10: 2026-05-19〜2026-05-23）— 経路候補算出 + 経路設計画面

```mermaid
gantt
    title イテレーション 2 - Week 2（経路候補算出 + 経路設計画面）
    dateFormat  YYYY-MM-DD
    section BE routingms
    RoutingSpec + Itinerary モデル  :a1, 2026-05-19, 1d
    RouteFinderService TDD          :a2, after a1, 2d
    RoutingController + テスト      :a3, after a2, 1d
    section FE routing
    useItineraries + 経路設計画面   :b1, 2026-05-22, 1d
    統合テスト + デモ準備           :b2, after b1, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | Task 4.1-4.2（RoutingSpec + Itinerary モデル） |
| Day 7-8 | Task 4.3（RouteFinderService TDD — 接続可能航海算出ロジック） |
| Day 9 | Task 4.4-4.5（RoutingController + 統合テスト） |
| Day 10 | Task 5（FE: 経路設計画面）+ 統合デモ準備 |

---

## 設計

### ドメインモデル

#### Booking Context（bookingms）

```plantuml
@startuml
class Cargo <<Aggregate Root>> {
    bookingId: BookingId
    shipper: ShipperId
    routeSpecification: RouteSpecification
    cargoType: CargoType
    weight: Weight
    status: BookingStatus
}

class RouteSpecification <<Value Object>> {
    origin: Location
    destination: Location
    arrivalDeadline: LocalDate
    +isSatisfiedBy(itinerary): boolean
}

class Weight <<Value Object>> {
    value: BigDecimal
    unit: String
}

' IT2 では PRELIMINARY / ROUTE_PROPOSED を使用
' 全状態値を定義して将来フェーズに備える
enum BookingStatus {
    PRELIMINARY
    ROUTE_PROPOSED
    CONFIRMED
    TRACKING_ISSUED
    IN_TRANSIT
    DELIVERED
    SETTLED
    CANCELLED
}

enum CargoType {
    GENERAL
    HAZARDOUS
    REFRIGERATED
}

Cargo *-- RouteSpecification
Cargo *-- Weight
Cargo -- BookingStatus
Cargo -- CargoType
@enduml
```

> **注**: Consignee・CargoItinerary・Delivery・HazardousDeclaration・TemperatureRequirement は domain-model.md に定義されているが IT2 スコープ外。IT3 以降で順次追加する。RouteSpecification.origin/destination は `Location` 値オブジェクト（共有カーネル）を参照するが、IT2 では UN/LOCODE 文字列として実装し、IT3 で Location VO に置き換える。

#### Routing Context — 経路候補（routingms 追加分）

```plantuml
@startuml
class Itinerary <<Value Object>> {
    legs: List<Leg>
    totalDays(): int
}

class Leg <<Value Object>> {
    voyage: VoyageNumber
    loadLocation: Location
    unloadLocation: Location
    loadTime: Date
    unloadTime: Date
}

class RoutingSpec <<Value Object>> {
    origin: String
    destination: String
    arrivalDeadline: LocalDate
}

Itinerary *-- Leg
@enduml
```

### データモデル

```sql
-- bookingms: cargo テーブル（data-model.md に準拠、IT2 スコープ列のみ）
CREATE TABLE cargo (
    id                       BIGSERIAL PRIMARY KEY,
    booking_id               VARCHAR(20)  NOT NULL UNIQUE,
    shipper_id               BIGINT       NOT NULL,
    booking_status           VARCHAR(30)  NOT NULL DEFAULT 'PRELIMINARY',
    transport_status         VARCHAR(30)  NOT NULL DEFAULT 'NOT_RECEIVED',
    routing_status           VARCHAR(30)  NOT NULL DEFAULT 'NOT_ROUTED',
    cargo_type               VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    weight_kg                NUMERIC(10,3) NOT NULL,
    spec_origin_unlocode     VARCHAR(5),
    spec_destination_unlocode VARCHAR(5),
    spec_arrival_deadline    DATE,
    booking_amount_value     INTEGER      NOT NULL DEFAULT 0,
    booking_amount_currency  VARCHAR(3)   NOT NULL DEFAULT 'JPY',
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

> **注**: カラム名は data-model.md の規約に準拠（`spec_origin_unlocode` / `spec_destination_unlocode` / `spec_arrival_deadline`）。`shipper_id` は BIGINT（FK）。`booking_amount` は Money 値オブジェクト対応のため value + currency の 2 カラム構成。`consignee_*` / `tracking_number` 等は IT3 以降に追加する。

### API 設計

| メソッド | エンドポイント | 説明 | ロール |
|---------|---------------|------|--------|
| `GET` | `/api/booking/v1/cargos` | 予約一覧（クエリフィルタ可） | ROLE_SALES |
| `POST` | `/api/booking/v1/cargos` | 新規予約登録 | ROLE_SALES |
| `GET` | `/api/booking/v1/cargos/:bookingId` | 予約詳細 | ROLE_SALES |
| `POST` | `/api/routing/v1/itineraries` | 経路候補算出 | ROLE_ROUTING |

### ユーザーインターフェース

#### 貨物予約一覧（/booking）

```plantuml
@startsalt
{+
  {/ <b>CargoTracker</b>    |    yamada@example.com  [ログアウト] }
  ==
  <b>貨物予約一覧</b>
  --
  {
    出発地 | "JPOSA          " | 目的地 | "USLAX          " | ステータス | ^すべて^ | [検索]
  }
  ==
  [+ 新規予約登録]
  {#
    **予約 ID** | **出発地** | **目的地** | **希望期限** | **ステータス** | **操作**
    BK-1234     | JPOSA      | USLAX      | 2026-04-15    | ROUTE_PROPOSED | [詳細]
    BK-1233     | JPYOK      | GBFXT      | 2026-04-20    | CONFIRMED      | [詳細]
  }
  ==
  < 前へ | 1 / 5 | 次へ >
}
@endsalt
```

#### 貨物予約登録（/booking/new）

```plantuml
@startsalt
{+
  {/ <b>CargoTracker</b>    |    yamada@example.com  [ログアウト] }
  ==
  <b>貨物予約登録</b>
  ==
  {
    出発地（UN/LOCODE） | "JPOSA         "
    目的地（UN/LOCODE） | "USLAX         "
    希望到着期限        | "2026-04-15    "
    貨物種別            | ^GENERAL^
    重量（kg）          | "1200          "
  }
  ==
  [登録する] | [キャンセル]
}
@endsalt
```

#### 経路設計（/routing/design/:bookingId）

```plantuml
@startsalt
{+
  {/ <b>CargoTracker</b>    |    yamada@example.com  [ログアウト] }
  ==
  <b>経路設計</b>  BK-1234
  --
  出発地: JPOSA　　目的地: USLAX　　希望期限: 2026-04-15
  ==
  <b>経路候補</b>
  {#
    **選択** | **航路番号** | **経由港** | **出発日** | **到着予定** | **所要日数**
    (*)      | V0042        | 直行       | 2026-04-01  | 2026-04-14   | 13 日
    ()       | V0045        | CNSHA 経由 | 2026-04-03  | 2026-04-16   | 13 日
  }
  ==
  [この経路を選択] | [キャンセル]
}
@endsalt
```

#### 画面遷移図

```plantuml
@startuml
title IT2 画面遷移図

[*] --> ダッシュボード

state ダッシュボード {
    ダッシュボード : /dashboard
}

state 貨物予約一覧 {
    貨物予約一覧 : /booking
    貨物予約一覧 : GET /api/booking/v1/cargos
}

state 貨物予約登録 {
    貨物予約登録 : /booking/new
    貨物予約登録 : POST /api/booking/v1/cargos
}

state 経路設計 {
    経路設計 : /routing/design/:bookingId
    経路設計 : POST /api/routing/v1/itineraries
}

ダッシュボード --> 貨物予約一覧 : [貨物予約] ナビ
貨物予約一覧 --> 貨物予約登録 : [+ 新規予約登録]
貨物予約登録 --> 貨物予約一覧 : 登録成功
貨物予約一覧 --> 経路設計 : [経路設計] リンク（ROLE_ROUTING）
経路設計 --> 貨物予約一覧 : 経路選択完了

@enduml
```

### ディレクトリ構成

#### バックエンド（bookingms）

```
apps/backend/bookingms/src/main/java/com/example/bookingms/
├── BookingApplication.java
├── interfaces/rest/
│   ├── CargoController.java
│   └── dto/
│       ├── CreateCargoRequest.java
│       ├── CargoResponse.java
│       └── CargoListResponse.java
├── application/internal/
│   ├── commandservices/CargoCommandService.java
│   └── queryservices/CargoQueryService.java
├── domain/model/
│   ├── aggregates/Cargo.java
│   └── valueobjects/
│       ├── BookingId.java
│       ├── RouteSpecification.java
│       ├── Weight.java
│       ├── CargoType.java
│       └── BookingStatus.java
└── infrastructure/repositories/
    ├── CargoMapper.java
    └── MyBatisCargoRepository.java
```

#### バックエンド（routingms 追加分）

```
apps/backend/routingms/src/main/java/com/example/routingms/
├── interfaces/rest/
│   ├── ItineraryController.java          ← 新規
│   └── dto/
│       ├── RoutingSpecRequest.java        ← 新規
│       └── ItineraryResponse.java         ← 新規
├── application/internal/
│   └── queryservices/RouteFinderService.java  ← 新規
└── domain/model/
    └── valueobjects/
        ├── Itinerary.java                 ← 新規
        ├── Leg.java                       ← 新規
        └── RoutingSpec.java               ← 新規
```

#### フロントエンド

```
apps/frontend/src/
├── features/
│   └── booking/
│       ├── components/
│       │   ├── BookingList.tsx
│       │   └── BookingForm.tsx
│       ├── hooks/useBookings.ts
│       └── types/booking.ts
├── pages/
│   ├── BookingListPage.tsx
│   ├── BookingNewPage.tsx
│   └── RoutingDesignPage.tsx
```

### ADR

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| 既存 ADR 参照 | ヘキサゴナルアーキテクチャ + MyBatis | 承認済み |

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| RouteFinderService の接続ロジックが複雑 | 高 | Day 7-8 の 2 日間を集中実装に充てる。まず直行便のみのシンプルケースで Green にしてから多区間を実装 |
| bookingms と routingms のデータ参照 | 中 | IT2 では API 分離を維持し、フロントエンドが両 API を直接呼び出す構成とする |
| UI ワイヤーフレームとの乖離（IT1 Problem） | 低 | FE 実装前に `docs/design/ui-design.md` のワイヤーフレームを必ず参照する |
| API パスと Gateway ルートのミスマッチ（IT1 Problem） | 低 | `/api/booking/**` の Gateway ルートを IT2 開始前に追加・確認済み |

---

## 完了条件

### Definition of Done

- [ ] コードレビュー完了（AI エージェントによる多角的レビュー実施）
- [ ] ユニットテスト（BE + FE）がパス
- [ ] 統合テスト（MockMvc + H2）がパス
- [ ] ArchUnit テストがパス
- [ ] Checkstyle / SpotBugs エラーなし（BUILD SUCCESSFUL）
- [ ] テストカバレッジ 80% 以上（JaCoCo / Vitest）
- [ ] E2E テストがパス（新規シナリオ追加）
- [ ] ドキュメント更新完了（iteration_plan-2.md 更新）

### デモ項目

1. 貨物予約登録（出発地・目的地・希望期限・貨物種別・重量を入力して登録）
2. 登録した予約が一覧に表示される（ステータス: PRELIMINARY）
3. 経路設計画面を開き、経路候補が推奨順に表示される
4. 直行便が最優先候補として表示される

### E2E テストシナリオ（新規追加）

| # | シナリオ | 期待結果 |
|---|---------|---------|
| 1 | 貨物予約一覧ページにアクセスできること | /booking に遷移し見出しが表示される |
| 2 | 新規予約登録フォームに遷移できること | /booking/new に遷移しフォームが表示される |
| 3 | 貨物予約を新規登録できること | 登録後 /booking に遷移し予約番号が表示される |
| 4 | 登録した予約を一覧で確認できること | PRELIMINARY ステータスで表示される |
| 5 | 経路設計画面で経路候補が表示されること | /routing/design/:bookingId で候補一覧が表示される |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-07 | 初版作成 | - |
| 2026-05-07 | 整合性検証結果を反映: 受入基準の IT2 スコープ明示・IT3 持ち越し記載、BookingStatus 全状態値追加、データモデル DDL を data-model.md に合わせて修正（shipper_id BIGINT/カラム名統一/Money カラム追加）、Leg の型を VoyageNumber/Location に修正、RouteFinderService の優先順序ロジック明記、E2E テストシナリオ追加 | - |

---

## 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 1 ふりかえり](./retrospective-1.md)
- [ユーザーストーリー](../requirements/user_story.md)
- [バックエンドアーキテクチャ](../design/architecture_backend.md)
- [UI 設計](../design/ui-design.md)
- [ドメインモデル設計](../design/domain-model.md)
- [データモデル設計](../design/data-model.md)
