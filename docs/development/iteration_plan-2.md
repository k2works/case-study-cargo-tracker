# イテレーション 2 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 |
| **期間** | 2026-06-04 〜 2026-06-17（2 週間） |
| **ゴール** | 荷主登録・法人荷主登録・貨物予約登録を実現し、予約ドメイン（bookingms）の基盤を確立する |
| **目標 SP** | 10 |

---

## ゴール

### イテレーション終了時の達成状態

1. **荷主管理**: 個人荷主・法人荷主（契約番号・割引率付き）の登録・参照が動作する
2. **貨物予約**: 標準貨物・危険物・冷凍貨物の予約登録が動作する
3. **品質維持**: テストカバレッジ 80% 以上（新規コード）、SonarQube Quality Gate PASS

### 成功基準

- [ ] US02: 荷主（個人）を登録・参照できる
- [ ] US03: 法人荷主（契約番号・割引率）を登録・参照できる
- [ ] US04: 標準貨物の予約を登録できる
- [ ] US05: 危険物・冷凍貨物の予約を登録できる
- [ ] テストカバレッジ 80% 以上（新規コード）
- [ ] SonarQube Quality Gate PASS

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 |
|----|-------------------|----|--------|
| US02 | 荷主を登録する | 2 | 必須 |
| US03 | 法人荷主を登録する | 2 | 必須 |
| US04 | 貨物予約を登録する | 3 | 必須 |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 | 必須 |
| **合計** | | **10** | |

### ストーリー詳細

#### US02: 荷主を登録する

**ストーリー**:

> 営業担当者として、新規荷主の氏名/社名・住所・連絡先・メールアドレスをシステムに登録したい。なぜなら、次回以降の予約で荷主情報の再入力を省略でき、顧客情報を一元管理できるからだ。

**受入条件**:

- [ ] 氏名/社名・住所・連絡先・メールアドレス・荷主種別（個人/法人）を入力できる
- [ ] 同一メールアドレスが既に登録されている場合、既存荷主として表示しどちらを使用するか選択できる
- [ ] 登録完了後、荷主 ID が発行される
- [ ] 荷主種別「個人」で登録できる

**対応 UC**: UC02

#### US03: 法人荷主を登録する

**ストーリー**:

> 営業担当者として、法人荷主の契約番号と割引率を含めて登録したい。なぜなら、法人契約条件（割引率）を精算時に自動適用できるからだ。

**受入条件**:

- [ ] 荷主種別「法人」を選択すると、法人契約情報（契約番号・割引率）の入力フィールドが表示される
- [ ] 割引率は 0〜30% の範囲で設定できる
- [ ] 法人荷主で登録完了後、荷主 ID が発行される
- [ ] 登録した法人情報は US22（法人割引を適用する）で参照される

**対応 UC**: UC02

#### US04: 貨物予約を登録する

**ストーリー**:

> 営業担当者として、荷主 ID・貨物仕様（種別・重量・寸法・個数・品名）・輸送条件（出発地・目的地・希望日）を入力して予約を登録したい。なぜなら、荷主の見積承認後に正式な予約を受け付け、経路設計フェーズに引き継げるからだ。

**受入条件**:

- [ ] 荷主 ID を入力して既存荷主を選択できる
- [ ] 貨物種別・重量・寸法・個数・品名を入力できる
- [ ] 出発地・目的地・希望引渡日・希望着日を入力できる
- [ ] 登録完了後、予約番号が発行され状態が「仮受付」になる
- [ ] 経路設計者に予約登録の通知が送信される
- [ ] 見積情報との整合性が確認される

**対応 UC**: UC03

#### US05: 危険物・冷凍貨物の予約を登録する

**ストーリー**:

> 営業担当者として、危険物や冷凍・冷蔵貨物の場合に、特別な追加情報（危険物申告・温度管理条件）を含めて予約を登録したい。なぜなら、貨物種別に応じた法的要件と取扱い条件を正確に管理し、安全な輸送を保証できるからだ。

**受入条件**:

- [ ] 貨物種別「危険物」を選択すると、危険物申告情報の入力フィールドが表示され入力が必須となる
- [ ] 貨物種別「冷凍・冷蔵貨物」を選択すると、温度管理条件の入力フィールドが表示され入力が必須となる
- [ ] 特別情報が登録された予約は、経路設計時に対応可能な航海・ルートのみが候補として表示される

**対応 UC**: UC03

---

## タスク

### 1. bookingms 基盤構築（1 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | bookingms Gradle サブモジュール作成 | 4h | - | [x] |
| 1.2 | Spring Boot + Axon 依存関係設定 | 2h | - | [x] |
| 1.3 | Flyway マイグレーション（shipper / cargo_summary テーブル） | 2h | - | [x] |

**小計**: 8h（理想時間）

### 2. US02: 荷主登録（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | Shipper 集約（RegisterShipperCommand / ShipperRegisteredEvent） | 3h | - | [x] |
| 2.2 | ShipperCommandService + ShipperQueryService | 2h | - | [x] |
| 2.3 | ShipperMapper（MyBatis）+ ShipperProjectionEventHandler | 2h | - | [x] |
| 2.4 | ShipperController（POST /api/v1/shippers / GET） | 2h | - | [x] |
| 2.5 | フロントエンド: 荷主一覧（S05）・荷主登録（S06） | 3h | - | [x] |
| 2.6 | テスト（Service / Controller / EventHandler） | 4h | - | [x] |

**小計**: 16h（理想時間）

### 3. US03: 法人荷主登録（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | Shipper 集約拡張（法人種別 + CorporateContract value object） | 3h | - | [x] |
| 3.2 | RegisterShipperCommand に契約番号・割引率フィールド追加 | 2h | - | [x] |
| 3.3 | フロントエンド: 荷主登録フォームに法人フィールド表示切替 | 2h | - | [x] |
| 3.4 | テスト（法人種別登録・割引率バリデーション） | 4h | - | [x] |

**小計**: 11h（理想時間）

### 4. US04: 貨物予約登録（3 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 4.1 | Cargo 集約（BookCargoCommand / CargoBookedEvent） | 4h | - | [x] |
| 4.2 | CargoCommandService + CargoQueryService | 2h | - | [x] |
| 4.3 | CargoMapper（MyBatis）+ cargo_summary EventHandler | 2h | - | [x] |
| 4.4 | CargoController（POST /api/v1/bookings / GET） | 2h | - | [x] |
| 4.5 | フロントエンド: 予約一覧（S08）・予約登録（S09） | 3h | - | [x] |
| 4.6 | テスト（Service / Controller / EventHandler） | 4h | - | [x] |

**小計**: 17h（理想時間）

### 5. US05: 危険物・冷凍貨物予約登録（3 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 5.1 | HazardInfo・TemperatureCondition value object 追加 | 3h | - | [ ] |
| 5.2 | CargoSpecification に特殊貨物情報統合 + バリデーション | 3h | - | [ ] |
| 5.3 | フロントエンド: 貨物種別選択による入力フィールド表示切替 | 3h | - | [ ] |
| 5.4 | テスト（バリデーション・フィールド必須チェック） | 4h | - | [ ] |

**小計**: 13h（理想時間）

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|---------|----|
| bookingms 基盤構築 | 0 | 8h | [ ] |
| US02: 荷主登録 | 2 | 16h | [ ] |
| US03: 法人荷主登録 | 2 | 11h | [ ] |
| US04: 貨物予約登録 | 3 | 17h | [ ] |
| US05: 危険物・冷凍貨物予約登録 | 3 | 13h | [ ] |
| **合計** | **10** | **65h** | |

**1 SP あたり**: 約 6.5h

**進捗率**: 80% (8/10 SP) — 2026-05-25: 基盤 + US02 + US03 + US04 完了（バックエンド 28 テスト + フロントエンド 37 テスト PASS、残 US05 2 SP）

---

## スケジュール

### Week 1（Day 1-5）

```mermaid
gantt
    title イテレーション 2 - Week 1
    dateFormat  YYYY-MM-DD
    section 基盤
    bookingms サブモジュール作成     :d1, 2026-06-04, 1d
    section 荷主登録
    Shipper 集約・サービス・マッパー :d2, after d1, 2d
    ShipperController + フロント    :d3, after d2, 1d
    section 法人荷主
    法人フィールド・CorporateContract:d4, 2026-06-09, 1d
```

| 日 | タスク |
|----|--------|
| Day 1（06-04） | bookingms サブモジュール作成、Flyway マイグレーション |
| Day 2（06-05） | Shipper 集約・CommandService・QueryService |
| Day 3（06-06） | ShipperMapper・EventHandler・Controller |
| Day 4（06-09） | 荷主登録フロントエンド（S05/S06）・テスト |
| Day 5（06-10） | Shipper 集約拡張（法人種別 / CorporateContract）・フォーム切替 |

### Week 2（Day 6-10）

```mermaid
gantt
    title イテレーション 2 - Week 2
    dateFormat  YYYY-MM-DD
    section 貨物予約登録
    Cargo 集約・サービス・マッパー        :a2, 2026-06-11, 2d
    CargoController + フロント           :a3, after a2, 1d
    section 特殊貨物
    HazardInfo/TemperatureCondition VO   :a4, 2026-06-16, 1d
    統合テスト・バグ修正                  :a5, after a4, 1d
```

| 日 | タスク |
|----|--------|
| Day 6（06-11） | 法人荷主登録テスト、Cargo 集約・CommandService・QueryService |
| Day 7（06-12） | CargoMapper・EventHandler・Controller |
| Day 8（06-13） | 予約登録フロントエンド（S08/S09）・テスト |
| Day 9（06-16） | HazardInfo・TemperatureCondition VO・バリデーション・フィールド切替 |
| Day 10（06-17） | 統合テスト、バグ修正、SonarQube 確認、デモ準備 |

---

## 設計

> **注**: domain-model.md・data-model.md・ui_design.md の定義に準拠する。

### ドメインモデル

```plantuml
@startuml
package "bookingms (Booking Context)" {

  class Shipper <<Aggregate Root>> {
    - shipperId: ShipperId
    - shipperType: ShipperType
    - name: ShipperName
    - address: Address
    - contact: ContactInfo
    - corporateContract: CorporateContract
    + handle(RegisterShipperCommand)
    + handle(UpdateContactCommand)
    + handle(AssignCorporateContractCommand)
    + apply(ShipperRegisteredEvent)
  }

  class ShipperId <<Value Object>> {
    - value: String
  }

  class ShipperName <<Value Object>> {
    - value: String
  }

  class Address <<Value Object>> {
    - addressLine1: String
    - addressLine2: String
    - city: String
    - countryCode: String
    - postalCode: String
  }

  class ContactInfo <<Value Object>> {
    - email: Email
    - phone: PhoneNumber
  }

  class Email <<Value Object>> {
    - value: String
  }

  class PhoneNumber <<Value Object>> {
    - value: String
  }

  enum ShipperType {
    INDIVIDUAL
    CORPORATE
  }

  class CorporateContract <<Value Object>> {
    - contractNumber: String
    - discountRate: Percentage
    ' discountRate は 0.000 〜 0.300（US22 で参照）
  }

  class Percentage <<Value Object>> {
    - value: BigDecimal
    + apply(amount: Money): Money
  }

  class Cargo <<Aggregate Root>> {
    - bookingId: BookingId
    - shipperId: ShipperId
    - cargoSpec: CargoSpecification
    - routeSpecification: RouteSpecification
    - bookingStatus: BookingStatus
    - routingStatus: RoutingStatus
    - trackingNumber: TrackingNumber
    - estimatedAmount: Money
    + handle(BookCargoCommand)
    + handle(AssignRouteToCargoCommand)
    + handle(ConfirmBookingCommand)
    + handle(CancelBookingCommand)
    + apply(CargoBookedEvent)
  }

  class BookingId <<Value Object>> {
    - value: String
  }

  class CargoSpecification <<Value Object>> {
    - cargoType: CargoType
    - weightKg: BigDecimal
    - dimensions: Dimensions
    - quantity: int
    - productName: String
    - hazardInfo: HazardInfo
    - temperatureCondition: TemperatureCondition
  }

  class Dimensions <<Value Object>> {
    - lengthCm: int
    - widthCm: int
    - heightCm: int
  }

  class HazardInfo <<Value Object>> {
    - imoClass: String
    - unNumber: String
    - declaration: String
  }

  class TemperatureCondition <<Value Object>> {
    - minCelsius: BigDecimal
    - maxCelsius: BigDecimal
  }

  class RouteSpecification <<Value Object>> {
    - origin: Location
    - destination: Location
    - arrivalDeadline: LocalDate
  }

  enum CargoType {
    GENERAL
    HAZARDOUS
    REFRIGERATED
  }

  enum BookingStatus {
    PRELIMINARY
    ROUTING
    ROUTE_PROPOSED
    CONFIRMED
    TRACKING_ISSUED
    IN_TRANSIT
    DELIVERED
    SETTLED
    CANCELLED
  }

  enum RoutingStatus {
    NOT_ROUTED
    ROUTED
    MISROUTED
  }

  Shipper *-- ShipperId
  Shipper *-- ShipperType
  Shipper *-- ShipperName
  Shipper *-- Address
  Shipper *-- ContactInfo
  Shipper *-- "0..1" CorporateContract
  ContactInfo *-- Email
  ContactInfo *-- PhoneNumber
  CorporateContract *-- Percentage

  Cargo *-- BookingId
  Cargo *-- CargoSpecification
  Cargo *-- RouteSpecification
  Cargo *-- BookingStatus
  Cargo *-- RoutingStatus
  CargoSpecification *-- CargoType
  CargoSpecification *-- Dimensions
  CargoSpecification *-- "0..1" HazardInfo
  CargoSpecification *-- "0..1" TemperatureCondition
  RouteSpecification *-- Location
}
@enduml
```

### データモデル

> **注**: data-model.md の booking_read_db（`shipper`・`cargo_summary`・`cargo_leg`）定義に準拠する。

```plantuml
@startuml
hide circle
skinparam linetype ortho

entity "shipper\n(booking_read_db)" as s {
  * **shipper_id**: VARCHAR(36) <<PK>>
  --
  shipper_type: VARCHAR(16) NOT NULL
  ' INDIVIDUAL / CORPORATE
  name: VARCHAR(200) NOT NULL
  address_line1: VARCHAR(200) NOT NULL
  address_line2: VARCHAR(200)
  city: VARCHAR(100) NOT NULL
  country_code: VARCHAR(2) NOT NULL
  postal_code: VARCHAR(20)
  email: VARCHAR(255) NOT NULL <<UNIQUE>>
  phone: VARCHAR(30) NOT NULL
  contract_number: VARCHAR(50)
  ' 法人のみ
  discount_rate: NUMERIC(4,3)
  ' 法人のみ 0.000〜0.300
  active: BOOLEAN NOT NULL DEFAULT TRUE
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "cargo_summary\n(booking_read_db)" as c {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  shipper_id: VARCHAR(36) NOT NULL <<FK>>
  tracking_number: VARCHAR(25) <<UNIQUE>>
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  arrival_deadline: DATE NOT NULL
  cargo_type: VARCHAR(16) NOT NULL
  ' GENERAL / HAZARDOUS / REFRIGERATED
  weight_kg: NUMERIC(12,2) NOT NULL
  length_cm: INTEGER
  width_cm: INTEGER
  height_cm: INTEGER
  quantity: INTEGER NOT NULL
  product_name: VARCHAR(200) NOT NULL
  hazard_imo_class: VARCHAR(20)
  hazard_un_number: VARCHAR(20)
  hazard_declaration: TEXT
  temperature_min_c: NUMERIC(5,2)
  temperature_max_c: NUMERIC(5,2)
  booking_status: VARCHAR(20) NOT NULL
  ' PRELIMINARY / ROUTING / ... / CANCELLED
  routing_status: VARCHAR(16) NOT NULL
  ' NOT_ROUTED / ROUTED / MISROUTED
  estimated_amount: NUMERIC(14,2)
  estimated_currency: VARCHAR(3)
  last_event_at: TIMESTAMPTZ
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "cargo_leg\n(booking_read_db)" as l {
  * **booking_id**: VARCHAR(36) <<PK>> <<FK>>
  * **leg_seq**: INTEGER <<PK>>
  --
  voyage_number: VARCHAR(20) NOT NULL
  load_unlocode: VARCHAR(5) NOT NULL
  unload_unlocode: VARCHAR(5) NOT NULL
  load_at: TIMESTAMPTZ NOT NULL
  unload_at: TIMESTAMPTZ NOT NULL
}

s ||--o{ c : "ships"
c ||--o{ l : "旅程 Leg"
@enduml
```

### ユーザーインターフェース

> **注**: ui_design.md の画面 ID・パスに準拠する。S05=`/shippers`、S06=`/shippers/new`、S07=`/shippers/:id`、S08=`/bookings`、S09=`/bookings/new`。

#### ビュー

```plantuml
@startsalt
{+
  S05: 荷主一覧（/shippers）
  {+
    { CargoTracker | 荷主管理 | [ログアウト] }
    ----
    {
      [新規荷主登録]
      ----
      | **荷主 ID** | **種別** | **氏名/社名** | **メール** | **登録日** | 操作 |
      | SHP-001 | 個人 | 山田 太郎 | t.yamada@... | 2026-06-05 | [詳細] |
      | SHP-002 | 法人 | ABC 商事 | info@abc... | 2026-06-06 | [詳細] |
    }
  }
-----------
  S06: 荷主登録（/shippers/new）
  {+
    { CargoTracker | 荷主管理 | [ログアウト] }
    ----
    {
      荷主種別 | (X) 個人  () 法人
      氏名/社名 | "               "
      住所（番地） | "           "
      市区町村  | "               "
      国コード  | "JP            "
      郵便番号  | "               "
      メールアドレス | "          "
      電話番号  | "               "
      ----
      == 法人契約情報（法人選択時のみ表示）==
      契約番号  | "               "
      割引率（%） | "  0 〜 30   "
      ----
      [登録する] [キャンセル]
    }
  }
-----------
  S08: 予約一覧（/bookings）
  {+
    { CargoTracker | 予約管理 | [ログアウト] }
    ----
    {
      [新規予約登録]
      ----
      | **予約番号** | **荷主** | **出発地** | **目的地** | **状態** | 操作 |
      | BK-001 | 山田 太郎 | JPTYO | USLAX | 仮受付 | [詳細] |
    }
  }
-----------
  S09: 予約登録（/bookings/new）
  {+
    { CargoTracker | 予約管理 | [ログアウト] }
    ----
    {
      荷主 ID  | "               "
      出発地（UN/LOCODE） | "   "
      目的地（UN/LOCODE） | "   "
      希望引渡日 | "             "
      希望着日  | "              "
      ----
      == 貨物仕様 ==
      貨物種別 | (X) 一般  () 危険物  () 冷凍冷蔵
      品名     | "               "
      重量（kg） | "             "
      寸法 L/W/H（cm） | "  /  / "
      個数     | "               "
      ----
      == 危険物申告（危険物選択時のみ・必須）==
      IMO クラス | "             "
      UN 番号    | "             "
      申告内容   | "             "
      ----
      == 温度管理条件（冷凍冷蔵選択時のみ・必須）==
      最低温度（℃） | "         "
      最高温度（℃） | "         "
      ----
      [登録する] [キャンセル]
    }
  }
}
@endsalt
```

#### モデル

```plantuml
@startuml
class 荷主一覧 {
  shippers: List<ShipperSummary>
  新規登録へ()
  詳細へ(shipperId)
}

class 荷主登録フォーム {
  shipperType: ShipperType
  name: String
  addressLine1: String
  city: String
  countryCode: String
  postalCode: String
  email: String
  phone: String
  contractNumber: String
  discountRate: BigDecimal
  登録する()
  キャンセル()
  法人フィールド表示切替(shipperType)
}

class 荷主詳細 {
  shipper: ShipperDetail
  連絡先更新へ()
}

class 予約一覧 {
  bookings: List<BookingSummary>
  新規登録へ()
  詳細へ(bookingId)
}

class 予約登録フォーム {
  shipperId: String
  originUnlocode: String
  destinationUnlocode: String
  arrivalDeadline: LocalDate
  希望引渡日: LocalDate
  cargoType: CargoType
  productName: String
  weightKg: BigDecimal
  dimensions: Dimensions
  quantity: int
  hazardInfo: HazardInput
  temperatureCondition: TempInput
  登録する()
  キャンセル()
  特殊フィールド表示切替(cargoType)
}

class ナビゲーション {
  ログアウト()
}

ナビゲーション -* 荷主一覧
ナビゲーション -* 予約一覧
荷主一覧 --> 荷主登録フォーム
荷主一覧 --> 荷主詳細
予約一覧 --> 予約登録フォーム
@enduml
```

#### インタラクション

```plantuml
@startuml
title 画面遷移図（IT2）

[*] --> S01 : ログイン済み

state "S01 ダッシュボード\n/dashboard" as S01
S01 --> S05 : サイドナビ「荷主管理」（営業担当者ロール）
S01 --> S08 : サイドナビ「予約管理」（営業担当者ロール）
S01 --> [*] : POST /auth/logout（PRG → /login）

state "S05 荷主一覧\n/shippers" as S05
S05 --> S06 : GET /shippers/new「新規荷主登録」
S05 --> S07 : GET /shippers/:id 行クリック

state "S06 荷主登録\n/shippers/new" as S06 : 荷主種別・基本情報を入力\n法人選択時：契約番号・割引率フィールド表示（US03）
S06 --> S06 : バリデーションエラー（自己ループ）\n・重複メール → 既存荷主表示・選択\n・割引率範囲外（0〜30%）→ エラー
S06 --> S07 : POST /api/v1/shippers 成功（PRG）

state "S07 荷主詳細\n/shippers/:id" as S07 : 登録内容の確認
S07 --> S05 : 「一覧に戻る」

state "S08 予約一覧\n/bookings" as S08
S08 --> S09 : GET /bookings/new「新規予約登録」
S08 --> S10 : GET /bookings/:id 行クリック（IT3 実装予定）

state "S09 予約登録\n/bookings/new" as S09 : 荷主・貨物仕様・輸送条件を入力\n危険物選択時：申告フィールド必須（US05）\n冷凍冷蔵選択時：温度条件フィールド必須（US05）
S09 --> S09 : バリデーションエラー（自己ループ）\n・荷主 ID 未存在\n・危険物申告フィールド未入力\n・温度条件フィールド未入力
S09 --> S08 : POST /api/v1/bookings 成功（PRG）\n' 状態：仮受付（PRELIMINARY）
@enduml
```

### API 設計

| メソッド | エンドポイント | 説明 |
|---------|---------------|------|
| POST | /api/v1/shippers | 荷主登録（個人・法人共通） |
| GET | /api/v1/shippers | 荷主一覧取得 |
| GET | /api/v1/shippers/{shipperId} | 荷主詳細取得 |
| POST | /api/v1/bookings | 予約登録（Cargo 集約） |
| GET | /api/v1/bookings | 予約一覧取得 |
| GET | /api/v1/bookings/{bookingId} | 予約詳細取得 |

### ディレクトリ構成

```
apps/backend/
└── bookingms/
    └── src/
        ├── main/java/.../bookingms/
        │   ├── application/
        │   │   ├── ShipperCommandService.java
        │   │   ├── ShipperQueryService.java
        │   │   ├── CargoCommandService.java
        │   │   └── CargoQueryService.java
        │   ├── domain/model/
        │   │   ├── Shipper.java          # 集約ルート（INDIVIDUAL/CORPORATE 共通）
        │   │   ├── Cargo.java            # 集約ルート（予約）
        │   │   └── vo/
        │   │       ├── CorporateContract.java
        │   │       ├── CargoSpecification.java
        │   │       ├── HazardInfo.java
        │   │       ├── TemperatureCondition.java
        │   │       ├── Dimensions.java
        │   │       ├── Address.java
        │   │       └── ContactInfo.java
        │   ├── infrastructure/
        │   │   ├── mapper/ShipperMapper.java
        │   │   └── mapper/CargoMapper.java
        │   └── interfaces/
        │       ├── events/ShipperProjectionEventHandler.java
        │       ├── events/CargoProjectionEventHandler.java
        │       ├── rest/ShipperController.java
        │       └── rest/CargoController.java
        └── resources/
            └── db/migration/
                ├── V2__create_shipper.sql
                └── V3__create_cargo_summary.sql

apps/frontend/src/
├── pages/
│   ├── shippers/
│   │   ├── ShipperListPage.tsx      # S05
│   │   ├── ShipperNewPage.tsx       # S06
│   │   └── ShipperDetailPage.tsx    # S07
│   └── bookings/
│       ├── BookingListPage.tsx      # S08
│       └── BookingNewPage.tsx       # S09
└── components/
    ├── shipper/
    │   ├── ShipperForm.tsx          # 種別切替フォーム
    │   └── CorporateContractFields.tsx
    └── booking/
        ├── BookingForm.tsx
        ├── HazardInfoFields.tsx     # US05: 危険物フィールド
        └── TemperatureConditionFields.tsx  # US05: 温度管理フィールド
```

### ADR

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| [ADR-0001](../adr/0001-axon-kafka-aiven-adoption.md) | Axon Kafka Extension + Aiven 採用 | 承認済み |
| [ADR-0002](../adr/0002-mybatis-adoption.md) | MyBatis 採用 | 承認済み |
| [ADR-0006](../adr/0006-heroku-deployment-setup.md) | Heroku Container Registry デプロイ構成 | 承認済み |

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| bookingms と routingms の Axon 設定が干渉する | 中 | BoundedContext を明確に分離し、Kafka トピックをマイクロサービス別に分ける |
| US03 の CorporateContract 実装がデータモデルと乖離する | 中 | `shipper` テーブルの `contract_number` / `discount_rate` に対応させ、法人種別時のみ必須とする |
| IT2 ベロシティが IT1 と乖離する | 低 | 10 SP を維持するが、US05 は US04 の後に実装し遅延時はバッファ調整する |

---

## 完了条件

### Definition of Done

- [ ] コードレビュー完了
- [ ] ユニットテストがパス（新規コードカバレッジ 80% 以上）
- [ ] E2E テストがパス
- [ ] SonarQube Quality Gate PASS（重複率 < 3%）
- [ ] ローカル環境（local-docker プロファイル）で動作確認済み
- [ ] Heroku デプロイ確認済み
- [ ] ドキュメント更新完了

### デモ項目

1. 個人荷主を登録し、荷主一覧（S05）に表示されることを確認
2. 法人荷主を登録し（契約番号・割引率付き）、法人として一覧に表示されることを確認
3. 標準貨物の予約を登録し（Cargo 集約）、予約番号発行・状態「仮受付」を確認
4. 危険物種別で予約登録し、危険物申告フィールドが必須表示されることを確認

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-22 | 初版作成 | k2works |
| 2026-05-22 | 整合性検証による修正（ストーリー文・受入条件・集約名・テーブル定義・画面 ID） | k2works |

---

## 関連ドキュメント

- [IT2 ふりかえり](./retrospective-2.md)
- [IT1 完了報告書](./iteration_report-1.md)
- [リリース計画](./release_plan.md)
- [ユーザーストーリー](../requirements/user_story.md)
- [ドメインモデル設計](../design/domain-model.md)
- [データモデル設計](../design/data-model.md)
- [UI 設計](../design/ui_design.md)
