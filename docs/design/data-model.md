---
title: データモデル設計 - 国際貨物輸送管理システム（Rust 版）
description: 国際貨物輸送管理システムの永続化層データモデル設計。概念データモデル・論理データモデル・テーブル定義・sqlx マイグレーション方針を含む。
published: true
date: 2026-07-06T00:00:00.000Z
tags: design, data-model, rust, sqlx, postgresql
---

# データモデル設計 - 国際貨物輸送管理システム

## 概要

本ドキュメントは、国際貨物輸送管理システムの永続化層データモデルを定義する。
ドメインモデル分析で識別した境界付けられたコンテキスト（Booking / Routing / Tracking / Handling / Billing / Estimation / Shared Domain）に対応する 16 テーブルと、認証用の `users` / `user_roles` の 2 テーブル（計 18 テーブル）を設計する。
`shipper`（荷主）テーブルを含む。

### 設計方針

- **DB**: PostgreSQL 16.x（本番・テスト共通。テストは testcontainers 等で PostgreSQL 実体を使用）
- **DB アクセス**: sqlx（コンパイル時検証 SQL。`query!` / `query_as!` マクロ）
- **マイグレーション**: sqlx migrate（`migrations/` ディレクトリ、`sqlx migrate run`）
- **ID 戦略**: サロゲートキー（`BIGSERIAL`）+ 業務キー（`VARCHAR`）の併用
- **命名規則**: スネークケース（PostgreSQL 慣習）
- **監査カラム**: 全テーブルに `created_at` / `updated_at` を付与

### PostgreSQL 型と Rust 型の対応

sqlx が行を Rust の struct（`#[derive(sqlx::FromRow)]`）へマッピングする際の型対応を以下に示す。

| PostgreSQL 型 | Rust 型 | 備考 |
| :--- | :--- | :--- |
| `BIGINT` / `BIGSERIAL` | `i64` | サロゲートキー |
| `INTEGER` / `INT` | `i32` | 金額（最小通貨単位）、順序番号等 |
| `VARCHAR(n)` / `TEXT` | `String` | ドメイン層では newtype（`BookingId` 等）に変換 |
| `BOOLEAN` | `bool` | |
| `NUMERIC(p,s)` | `rust_decimal::Decimal` | 重量・税率・見積コスト等 |
| `TIMESTAMP WITH TIME ZONE` | `chrono::DateTime<chrono::Utc>` | 監査カラム・イベント日時 |
| `DATE` | `chrono::NaiveDate` | 到着期限・支払期日 |
| `UUID` | `uuid::Uuid` | 荷主 ID・見積 ID 等の業務キー |
| 列挙値カラム（`VARCHAR(30)`） | Rust `enum` + `#[derive(sqlx::Type)]` | `#[sqlx(type_name = "VARCHAR", rename_all = "SCREAMING_SNAKE_CASE")]` 等で文字列マッピング |
| NULLable カラム | `Option<T>` | |

```rust
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;

#[derive(Debug, sqlx::FromRow)]
pub struct CargoRow {
    pub id: i64,
    pub booking_id: String,
    pub shipper_id: uuid::Uuid,
    pub booking_status: String, // ドメイン層で BookingStatus enum に変換
    pub cargo_type: String,
    pub weight_kg: Decimal,
    pub declared_value: Option<Decimal>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}
```

---

## 概念データモデル

全コンテキストのエンティティとその主要リレーションシップを俯瞰する。

```plantuml
@startuml
title 概念データモデル - 国際貨物輸送管理システム

skinparam entity {
  BackgroundColor White
  BorderColor Black
}

package "Shared Domain" #lightgray {
  entity "location\n（場所）" as location {
    * id : BIGINT <<PK>>
    --
    * unlocode : VARCHAR(5) <<UK>>
    * name : VARCHAR(100)
  }

  entity "users\n（ユーザー）" as users {
    * id : BIGINT <<PK>>
    --
    * username : VARCHAR(50) <<UK>>
    * email : VARCHAR(200) <<UK>>
    * password : VARCHAR(255)
    * enabled : BOOLEAN
  }

  entity "user_roles\n（ユーザーロール）" as user_roles {
    * user_id : BIGINT <<FK, PK>>
    * role : VARCHAR(50) <<PK>>
  }
}

package "Booking Context" #lightblue {
  entity "shipper\n（荷主）" as shipper {
    * id : UUID <<PK>>
    --
    * shipper_code : VARCHAR(20) <<UK>>
    * shipper_type : VARCHAR(20)
    * name : VARCHAR(200)
    * email : VARCHAR(200)
  }

  entity "cargo\n（貨物）" as cargo {
    * id : BIGINT <<PK>>
    --
    * booking_id : VARCHAR(20) <<UK>>
    * shipper_id : UUID <<FK>>
    * booking_status : VARCHAR(30)
    * transport_status : VARCHAR(30)
    * routing_status : VARCHAR(30)
    * cargo_type : VARCHAR(20)
    * weight_kg : NUMERIC(10,3)
    declared_value : NUMERIC(15,2)
    * booking_amount_value : INTEGER
    * booking_amount_currency : VARCHAR(3)
  }

  entity "leg\n（輸送区間）" as leg {
    * id : BIGINT <<PK>>
    --
    * cargo_id : BIGINT <<FK>>
    * voyage_number : VARCHAR(20) <<FK>>
    * load_location_unlocode : VARCHAR(5) <<FK>>
    * unload_location_unlocode : VARCHAR(5) <<FK>>
    * load_time : TIMESTAMP
    * unload_time : TIMESTAMP
  }
}

package "Routing Context" #lightgreen {
  entity "voyage\n（航海）" as voyage {
    * id : BIGINT <<PK>>
    --
    * voyage_number : VARCHAR(20) <<UK>>
  }

  entity "carrier_movement\n（運送区間）" as carrier_movement {
    * id : BIGINT <<PK>>
    --
    * voyage_id : BIGINT <<FK>>
    * departure_location_unlocode : VARCHAR(5) <<FK>>
    * arrival_location_unlocode : VARCHAR(5) <<FK>>
    * departure_date : TIMESTAMP
    * arrival_date : TIMESTAMP
  }
}

package "Tracking Context" #lightyellow {
  entity "tracking_activity\n（追跡レコード）" as tracking_activity {
    * id : BIGINT <<PK>>
    --
    * tracking_number : VARCHAR(20) <<UK>>
    * booking_id : VARCHAR(20)
    * transport_status : VARCHAR(30)
  }

  entity "tracking_handling_event\n（追跡イベント）" as tracking_handling_event {
    * id : BIGINT <<PK>>
    --
    * tracking_id : BIGINT <<FK>>
    * event_type : VARCHAR(30)
    * event_time : TIMESTAMP
    * location_unlocode : VARCHAR(5) <<FK>>
    * voyage_number : VARCHAR(20)
  }

  entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
    * id : BIGINT <<PK>>
    --
    * tracking_id : BIGINT <<FK>>
    * exception_type : VARCHAR(50)
    * occurred_at : TIMESTAMP
    * escalation_flag : BOOLEAN
    description : VARCHAR(500)
    resolved_at : TIMESTAMP
    resolution_notes : TEXT
  }
}

package "Handling Context" #lightcoral {
  entity "handling_activity\n（荷役作業記録）" as handling_activity {
    * id : BIGINT <<PK>>
    --
    * booking_id : VARCHAR(20)
    * event_type : VARCHAR(30)
    * event_completion_time : TIMESTAMP
    * location_unlocode : VARCHAR(5) <<FK>>
    * voyage_number : VARCHAR(20)
  }

  entity "customs_declaration\n（税関申告）" as customs_declaration {
    * id : BIGINT <<PK>>
    --
    * handling_activity_id : BIGINT <<FK>>
    * declaration_number : VARCHAR(50) <<UK>>
    * declared_at : TIMESTAMP
    * status : VARCHAR(30)
  }
}

package "Estimation Context" #wheat {
  entity "estimate\n（見積）" as estimate {
    * id : BIGINT <<PK>>
    --
    * estimate_id : UUID <<UK>>
    * origin_unlocode : VARCHAR(5)
    * destination_unlocode : VARCHAR(5)
    * arrival_deadline : DATE
    * cargo_type : VARCHAR(30)
    * weight_kg : NUMERIC(10,3)
    * status : VARCHAR(20)
  }

  entity "route_candidate\n（ルート候補）" as route_candidate {
    * id : BIGINT <<PK>>
    --
    * estimate_id : BIGINT <<FK>>
    * voyage_number : VARCHAR(20)
    transit_port : VARCHAR(5)
    * transit_days : INT
    * estimated_cost : NUMERIC(12,2)
    * rank : INT
  }
}

package "Billing Context" #lightpink {
  entity "invoice\n（精算書）" as invoice {
    * id : BIGINT <<PK>>
    --
    * invoice_number : VARCHAR(30) <<UK>>
    * booking_id : VARCHAR(20) <<UK>>
    * total_amount_value : INTEGER
    * total_amount_currency : VARCHAR(3)
    * tax_rate : NUMERIC(5,4)
    * tax_amount : NUMERIC(15,2)
    * payment_status : VARCHAR(30)
  }

  entity "invoice_line_item\n（精算明細）" as invoice_line_item {
    * id : BIGINT <<PK>>
    --
    * invoice_id : BIGINT <<FK>>
    * description : VARCHAR(200)
    * amount_value : INTEGER
    * amount_currency : VARCHAR(3)
  }

  entity "payment\n（支払記録）" as payment {
    * id : BIGINT <<PK>>
    --
    * invoice_id : BIGINT <<FK>>
    * paid_amount_value : INTEGER
    * paid_amount_currency : VARCHAR(3)
    * paid_at : TIMESTAMP
    * payment_method : VARCHAR(30)
  }
}

' Booking Context relations
cargo }o--|| shipper : "荷主"
cargo ||--o{ leg : "旅程を持つ"
leg }o--|| voyage : "航海を参照"
leg }o--|| location : "積込場所"
leg }o--|| location : "荷降場所"
cargo }o--o| location : "出発地"
cargo }o--o| location : "仕向地"

' Routing Context relations
voyage ||--o{ carrier_movement : "運送区間を持つ"
carrier_movement }o--|| location : "出発地"
carrier_movement }o--|| location : "到着地"

' Tracking Context relations
tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"
tracking_handling_event }o--o| location : "発生場所"

' Handling Context relations
handling_activity ||--o| customs_declaration : "税関申告を持つ"
handling_activity }o--|| location : "作業場所"

' Estimation Context relations
estimate ||--o{ route_candidate : "ルート候補を持つ"

' Billing Context relations
invoice ||--o{ invoice_line_item : "明細を持つ"
invoice ||--o{ payment : "支払を持つ"

' Security relations
users ||--o{ user_roles : "ロールを持つ"

@enduml
```

---

## 論理データモデル

### Shared Domain

共有ドメインとして全コンテキストが参照する場所マスタ。UN/LOCODE（国連貿易港コード）を業務キーとする。

```plantuml
@startuml
title 論理データモデル - Shared Domain

entity "location\n（場所）" as location {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * unlocode : VARCHAR(5) <<UK, NOT NULL>>
  * name : VARCHAR(100) <<NOT NULL>>
  country_code : VARCHAR(2)
  time_zone : VARCHAR(50)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

@enduml
```

---

### Booking Context

貨物の予約・旅程情報を管理する。`cargo` が集約ルートで、`leg` が旅程の各区間を表す。荷主情報は `shipper` テーブルに正規化し、FK 参照とする。

```plantuml
@startuml
title 論理データモデル - Booking Context

entity "shipper\n（荷主）" as shipper {
  * id : UUID <<PK>>
  --
  * shipper_code : VARCHAR(20) <<UK, NOT NULL>>
  * shipper_type : VARCHAR(20) <<NOT NULL>>
  * name : VARCHAR(200) <<NOT NULL>>
  * email : VARCHAR(200) <<NOT NULL>>
  phone : VARCHAR(50)
  contract_number : VARCHAR(50)
  discount_rate : NUMERIC(5,4)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "cargo\n（貨物）" as cargo {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : VARCHAR(20) <<UK, NOT NULL>>
  * shipper_id : UUID <<FK, NOT NULL>>
  * booking_status : VARCHAR(30) <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
  * routing_status : VARCHAR(30) <<NOT NULL>>
  * cargo_type : VARCHAR(20) <<NOT NULL, DEFAULT 'GENERAL'>>
  * weight_kg : NUMERIC(10,3) <<NOT NULL>>
  declared_value : NUMERIC(15,2)
  spec_origin_unlocode : VARCHAR(5) <<FK>>
  spec_destination_unlocode : VARCHAR(5) <<FK>>
  spec_arrival_deadline : DATE
  origin_unlocode : VARCHAR(5) <<FK>>
  * booking_amount_value : INTEGER <<NOT NULL>>
  * booking_amount_currency : VARCHAR(3) <<NOT NULL>>
  consignee_name : VARCHAR(200)
  consignee_email : VARCHAR(200)
  tracking_number : VARCHAR(20)
  next_expected_location_unlocode : VARCHAR(5)
  next_expected_handling_event_type : VARCHAR(30)
  next_expected_voyage_number : VARCHAR(20)
  last_known_location_unlocode : VARCHAR(5)
  current_voyage_number : VARCHAR(20)
  last_handling_event_type : VARCHAR(30)
  last_handling_event_location : VARCHAR(5)
  last_handling_event_voyage : VARCHAR(20)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "leg\n（輸送区間）" as leg {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * cargo_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<FK, NOT NULL>>
  * load_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * unload_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  load_time : TIMESTAMP
  unload_time : TIMESTAMP
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

cargo }o--|| shipper : "荷主"
cargo ||--o{ leg : "旅程を持つ"

@enduml
```

---

### Routing Context

航海スケジュールと運送区間を管理する。`voyage` が集約ルートで、`carrier_movement` が個々の移動区間を表す。

```plantuml
@startuml
title 論理データモデル - Routing Context

entity "voyage\n（航海）" as voyage {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * voyage_number : VARCHAR(20) <<UK, NOT NULL>>
  * vessel_name : VARCHAR(100) <<NOT NULL>>
  * carrier : VARCHAR(100) <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "voyage_cargo_type\n（航海対応貨物種別）" as voyage_cargo_type {
  * voyage_id : BIGINT <<PK, FK, NOT NULL>>
  * cargo_type : VARCHAR(30) <<PK, NOT NULL>>
}

entity "carrier_movement\n（運送区間）" as carrier_movement {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * voyage_id : BIGINT <<FK, NOT NULL>>
  * departure_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * arrival_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * departure_date : TIMESTAMP <<NOT NULL>>
  * arrival_date : TIMESTAMP <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

voyage ||--o{ carrier_movement : "運送区間を持つ"
voyage ||--o{ voyage_cargo_type : "対応貨物種別を持つ"

@enduml
```

---

### Tracking Context

貨物追跡の状態・イベント・例外を管理する。`tracking_activity` が集約ルート。

```plantuml
@startuml
title 論理データモデル - Tracking Context

entity "tracking_activity\n（追跡レコード）" as tracking_activity {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_number : VARCHAR(20) <<UK, NOT NULL>>
  * booking_id : VARCHAR(20) <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "tracking_handling_event\n（追跡イベント）" as tracking_handling_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_time : TIMESTAMP <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK>>
  voyage_number : VARCHAR(20)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * exception_type : VARCHAR(50) <<NOT NULL>>
  * occurred_at : TIMESTAMP <<NOT NULL>>
  * escalation_flag : BOOLEAN <<NOT NULL, DEFAULT FALSE>>
  description : VARCHAR(500)
  resolved_at : TIMESTAMP WITH TIME ZONE
  resolution_notes : TEXT
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"

@enduml
```

---

### Handling Context

荷役作業の実績と税関申告を管理する。`handling_activity` が集約ルート。

```plantuml
@startuml
title 論理データモデル - Handling Context

entity "handling_activity\n（荷役作業記録）" as handling_activity {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : VARCHAR(20) <<NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_completion_time : TIMESTAMP <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  voyage_number : VARCHAR(20)
  operator_name : VARCHAR(200)
  receipt_confirmation : VARCHAR(200)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

' receipt_confirmation は引取（CLAIM）時の荷受人確認（署名／確認コード。US16・IT5 実装）

entity "customs_declaration\n（税関申告）" as customs_declaration {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * handling_activity_id : BIGINT <<FK, NOT NULL>>
  * declaration_number : VARCHAR(50) <<UK, NOT NULL>>
  * declared_at : TIMESTAMP <<NOT NULL>>
  * status : VARCHAR(30) <<NOT NULL>>
  cleared_at : TIMESTAMP
  remarks : VARCHAR(500)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

handling_activity ||--o| customs_declaration : "税関申告を持つ"

@enduml
```

---

### Billing Context

精算書・明細・支払記録を管理する。参考実装には存在しない新規コンテキスト。

```plantuml
@startuml
title 論理データモデル - Billing Context

entity "invoice\n（精算書）" as invoice {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_number : VARCHAR(30) <<UK, NOT NULL>>
  * booking_id : VARCHAR(20) <<UK, NOT NULL>>
  * total_amount_value : INTEGER <<NOT NULL>>
  * total_amount_currency : VARCHAR(3) <<NOT NULL>>
  * tax_rate : NUMERIC(5,4) <<NOT NULL, DEFAULT 0.1000>>
  * tax_amount : NUMERIC(15,2) <<NOT NULL, DEFAULT 0>>
  * payment_status : VARCHAR(30) <<NOT NULL>>
  issued_at : TIMESTAMP
  due_date : DATE
  discount_amount_value : INTEGER
  discount_amount_currency : VARCHAR(3)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "invoice_line_item\n（精算明細）" as invoice_line_item {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * description : VARCHAR(200) <<NOT NULL>>
  * amount_value : INTEGER <<NOT NULL>>
  * amount_currency : VARCHAR(3) <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "payment\n（支払記録）" as payment {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * paid_amount_value : INTEGER <<NOT NULL>>
  * paid_amount_currency : VARCHAR(3) <<NOT NULL>>
  * paid_at : TIMESTAMP <<NOT NULL>>
  * payment_method : VARCHAR(30) <<NOT NULL>>
  transaction_reference : VARCHAR(100)
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

invoice ||--o{ invoice_line_item : "明細を持つ"
invoice ||--o{ payment : "支払を持つ"

@enduml
```

#### freight_charge（輸送料金・US21/US22・IT7 実装）

輸送料金の算出結果を管理する。参考実装・IT6 時点の Billing 設計には無く、IT7 で新規追加した。
`invoice`（精算書・US23/IT8）とは責務を分離し、`freight_charge`（確定した輸送料金）が精算書生成の入力となる（段階分割・[ADR-0009](../adr/0009-freight-charge-and-invoice-separation.md)）。予約 1 件に 1 料金（`booking_id` UNIQUE・二重算出防止・冪等 upsert）。

```plantuml
@startuml
title 論理データモデル - Billing Context（freight_charge・IT7 追加）

entity "freight_charge\n（輸送料金）" as freight_charge {
  * id : BIGINT <<PK, IDENTITY>>
  --
  * charge_id : VARCHAR(40) <<UK, NOT NULL>>   FRC-<uuid>
  * booking_id : VARCHAR(20) <<UK, NOT NULL>>
  * base_amount_value : NUMERIC(15,2) <<NOT NULL>>
  * base_amount_currency : VARCHAR(3) <<NOT NULL, DEFAULT 'JPY'>>
  discount_rate : NUMERIC(5,4)
  discount_amount_value : NUMERIC(15,2)
  * total_amount_value : NUMERIC(15,2) <<NOT NULL>>
  * total_amount_currency : VARCHAR(3) <<NOT NULL, DEFAULT 'JPY'>>
  * status : VARCHAR(20) <<NOT NULL, DEFAULT 'DRAFT'>>   DRAFT / CONFIRMED
  confirmed_at : TIMESTAMPTZ
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "freight_charge_adjustment\n（料金調整）" as freight_charge_adjustment {
  * id : BIGINT <<PK, IDENTITY>>
  --
  * freight_charge_id : BIGINT <<FK, NOT NULL>>
  * reason : VARCHAR(30) <<NOT NULL>>   DELAY_REDUCTION / DAMAGE_COMPENSATION
  * amount_value : NUMERIC(15,2) <<NOT NULL>>
  * amount_currency : VARCHAR(3) <<NOT NULL, DEFAULT 'JPY'>>
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

freight_charge ||--o{ freight_charge_adjustment : "調整を持つ"
@enduml
```

マイグレーション: `20260930000001_it7_billing_charge.sql`。

---

### Estimation Context

輸送見積とルート候補を管理する。`estimate` が集約ルートで、`route_candidate` が各ルート候補を表す。

```plantuml
@startuml
title 論理データモデル - Estimation Context

entity "estimate\n（見積）" as estimate {
  * id : BIGINT <<PK, GENERATED BY DEFAULT AS IDENTITY>>
  --
  * estimate_id : UUID <<UK, NOT NULL>>
  * origin_unlocode : VARCHAR(5) <<NOT NULL>>
  * destination_unlocode : VARCHAR(5) <<NOT NULL>>
  * arrival_deadline : DATE <<NOT NULL>>
  * cargo_type : VARCHAR(30) <<NOT NULL>>
  * weight_kg : NUMERIC(10,3) <<NOT NULL>>
  * status : VARCHAR(20) <<NOT NULL, DEFAULT 'CREATED'>>
  * created_at : TIMESTAMP WITH TIME ZONE <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP WITH TIME ZONE <<NOT NULL, DEFAULT NOW()>>
}

entity "route_candidate\n（ルート候補）" as route_candidate {
  * id : BIGINT <<PK, GENERATED BY DEFAULT AS IDENTITY>>
  --
  * estimate_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<NOT NULL>>
  transit_port : VARCHAR(5)
  * transit_days : INT <<NOT NULL>>
  * estimated_cost : NUMERIC(12,2) <<NOT NULL>>
  * rank : INT <<NOT NULL, DEFAULT 0>>
}

estimate ||--o{ route_candidate : "ルート候補を持つ"

@enduml
```

---

### Security Context

axum の認証ミドルウェア（セッション / JWT 検証層）が利用するユーザー認証・認可テーブル。

```plantuml
@startuml
title 論理データモデル - Security Context

entity "users\n（ユーザー）" as users {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * username : VARCHAR(50) <<UK, NOT NULL>>
  * email : VARCHAR(200) <<UK, NOT NULL>>
  * password : VARCHAR(255) <<NOT NULL>>
  * enabled : BOOLEAN <<NOT NULL, DEFAULT TRUE>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

entity "user_roles\n（ユーザーロール）" as user_roles {
  * user_id : BIGINT <<FK, PK>>
  * role : VARCHAR(50) <<PK>>
}

users ||--o{ user_roles : "ロールを持つ"

@enduml
```

---

## テーブル定義

### `location`（場所マスタ）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `unlocode` | `VARCHAR(5)` | `UK, NOT NULL` | UN/LOCODE（業務キー。例: `JPTYO`） |
| `name` | `VARCHAR(100)` | `NOT NULL` | 場所名称（例: `Tokyo`） |
| `country_code` | `VARCHAR(2)` | | ISO 3166-1 alpha-2 国コード |
| `time_zone` | `VARCHAR(50)` | | タイムゾーン（例: `Asia/Tokyo`） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `shipper`（荷主）

> **注記**: 旧設計で `cargo` テーブルに存在した `shipper_name`・`shipper_email` カラムは本テーブルへの正規化に伴い削除した。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PK, NOT NULL` | 主キー（ドメインの `ShipperId`。`uuid::Uuid`） |
| `shipper_code` | `VARCHAR(20)` | `UK, NOT NULL` | 荷主コード（業務キー。SHP-XXXXXX 形式） |
| `shipper_type` | `VARCHAR(20)` | `NOT NULL` | 荷主種別（`INDIVIDUAL` / `CORPORATE`） |
| `name` | `VARCHAR(200)` | `NOT NULL` | 荷主名称 |
| `email` | `VARCHAR(200)` | `UK, NOT NULL` | メールアドレス（システム全体で一意） |
| `phone` | `VARCHAR(50)` | | 電話番号 |
| `contract_number` | `VARCHAR(50)` | | 契約番号（法人のみ。NULLable） |
| `discount_rate` | `NUMERIC(5,4)` | `DEFAULT 0.0000` | 割引率（0.0000〜0.3000、最大 30%。[ドメインモデル](domain-model.md) の DiscountRate 値域に準拠） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### DDL

```sql
CREATE TABLE shipper (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipper_code    VARCHAR(20)  NOT NULL UNIQUE,  -- SHP-XXXXXX 形式
    shipper_type    VARCHAR(20)  NOT NULL,          -- INDIVIDUAL / CORPORATE
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL UNIQUE,  -- システム全体で一意（ドメインルール）
    phone           VARCHAR(50),
    contract_number VARCHAR(50),                   -- 法人のみ（NULLable）
    discount_rate   NUMERIC(5,4) DEFAULT 0.0000,   -- 0.0000〜0.3000 (最大 30%)
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

---

### `cargo`（貨物）

> **注記**: `shipper_name`・`shipper_email` カラムは削除し、`shipper_id`（FK → `shipper.id`）による参照に変更した。
>
> 将来フェーズで追加予定のカラム（`transport_status`・`routing_status`・`booking_amount_*`・`tracking_number` 等）は下表の「将来追加予定」節に記載する。
>
> **本「テーブル定義」節を実装の Single Source of Truth（マイグレーション基準）とする。**「論理データモデル」節の cargo ER は全カラムを俯瞰する概念ビュー（将来追加分を含むスーパーセット）であり、カラム名・型・段階導入は本節に従う（カラム名は `origin_unlocode`/`destination_unlocode`/`arrival_deadline`、`booking_id` は `VARCHAR(20)`）。荷受人（`consignee_*`）は予約登録（US04）で必須のため IT1 の基本カラムとする。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（`BIGINT GENERATED BY DEFAULT AS IDENTITY`） |
| `booking_id` | `VARCHAR(20)` | `UK, NOT NULL` | 予約 ID（業務キー。ドメインの `BookingId` newtype・`FromRow` は `String`） |
| `shipper_id` | `UUID` | `FK → shipper.id, NOT NULL` | 荷主 ID |
| `cargo_type` | `VARCHAR(30)` | `NOT NULL` | 貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`） |
| `weight` | `NUMERIC(10,3)` | `NOT NULL, > 0` | 重量（kg） |
| `origin_unlocode` | `VARCHAR(5)` | `NOT NULL` | 出発地（RouteSpecification） |
| `destination_unlocode` | `VARCHAR(5)` | `NOT NULL` | 仕向地（RouteSpecification） |
| `arrival_deadline` | `DATE` | `NOT NULL` | 到着期限（RouteSpecification） |
| `consignee_name` | `VARCHAR(200)` | `NOT NULL` | 荷受人名（予約登録で必須。US04） |
| `consignee_email` | `VARCHAR(200)` | `NOT NULL` | 荷受人連絡先（予約登録で必須。US04） |
| `booking_status` | `VARCHAR(30)` | `NOT NULL, DEFAULT 'PRELIMINARY'` | 予約状態（BookingStatus 列挙値。許容値: `PRELIMINARY`/`ROUTE_DESIGNING`/`ROUTE_PROPOSED`/`CONFIRMED`/`TRACKING_ISSUED`/`IN_TRANSIT`/`DELIVERED`/`SETTLED`/`CANCELLED`。値の妥当性は Rust enum が担保しスキーマ制約は設けない） |
| `dimension_length` | `NUMERIC(10,3)` | | 貨物の長さ（cm、オプション） |
| `dimension_width` | `NUMERIC(10,3)` | | 貨物の幅（cm、オプション） |
| `dimension_height` | `NUMERIC(10,3)` | | 貨物の高さ（cm、オプション） |
| `quantity` | `INTEGER` | | 貨物個数（オプション、1 以上） |
| `description` | `VARCHAR(500)` | | 品名（オプション） |
| `hazardous_class` | `VARCHAR(10)` | | 危険物クラス（HAZARDOUS 時のみ） |
| `un_number` | `VARCHAR(10)` | | UN 番号（HAZARDOUS 時のみ） |
| `proper_shipping_name` | `VARCHAR(200)` | | 正式輸送品名（HAZARDOUS 時のみ） |
| `min_temperature` | `NUMERIC(10,3)` | | 最低温度（REFRIGERATED 時のみ） |
| `max_temperature` | `NUMERIC(10,3)` | | 最高温度（REFRIGERATED 時のみ） |
| `temperature_unit` | `VARCHAR(20)` | | 温度単位（`CELSIUS` / `FAHRENHEIT`、REFRIGERATED 時のみ） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### 将来追加予定カラム

| カラム名 | データ型 | 説明 | 追加フェーズ |
| :--- | :--- | :--- | :--- |
| `transport_status` | `VARCHAR(30)` | 輸送状態（TransportStatus 列挙値） | Tracking Context 実装時 |
| `routing_status` | `VARCHAR(30)` | 経路決定状態（ROUTED / MISROUTED / NOT_ROUTED） | Routing Context 実装時 |
| `booking_amount_value` | `INTEGER` | 予約金額（最小通貨単位） | Billing Context 実装時 |
| `booking_amount_currency` | `VARCHAR(3)` | 通貨コード（ISO 4217） | Billing Context 実装時 |
| `tracking_number` | `VARCHAR(20)` | 追跡番号（発行後に設定） | Tracking Context 実装時 |
| `next_expected_*` | 各種 | 次の予定荷役情報 | Tracking Context 実装時 |
| `last_handling_event_*` | 各種 | 最後の荷役イベント情報 | Handling Context 実装時 |

---

### `leg`（輸送区間）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `cargo_id` | `BIGINT` | `FK → cargo.id, NOT NULL` | 親貨物 ID |
| `voyage_number` | `VARCHAR(20)` | `FK → voyage.voyage_number, NOT NULL` | 航海番号 |
| `load_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 積込場所（UN/LOCODE） |
| `unload_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 荷降場所（UN/LOCODE） |
| `load_time` | `TIMESTAMP` | | 積込予定日時 |
| `unload_time` | `TIMESTAMP` | | 荷降予定日時 |
| `seq_number` | `INTEGER` | `NOT NULL` | 区間順序（1 始まり） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `voyage`（航海）

> **注記（IT2 追加）**: US24 の受入基準（船名・運送会社・対応貨物種別の入力）を満たすため、`vessel_name`・`carrier` カラムを追加した。対応貨物種別は 1 航海が複数種別に対応しうるため、`voyage` テーブルのカラムではなく子テーブル `voyage_cargo_type`（下記）へ正規化した。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `voyage_number` | `VARCHAR(20)` | `UK, NOT NULL` | 航海番号（業務キー） |
| `vessel_name` | `VARCHAR(100)` | `NOT NULL` | 船名（US24） |
| `carrier` | `VARCHAR(100)` | `NOT NULL` | 運送会社（US24） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `voyage_cargo_type`（航海対応貨物種別）

> **注記（IT2 追加）**: 1 航海が対応する貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`）を保持する子テーブル。US07 の貨物種別絞り込みの制約評価に用いる。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `voyage_id` | `BIGINT` | `PK, FK → voyage.id, NOT NULL` | 親航海 ID（CASCADE 削除） |
| `cargo_type` | `VARCHAR(30)` | `PK, NOT NULL` | 対応貨物種別（CargoType 列挙値） |

---

### `carrier_movement`（運送区間）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `voyage_id` | `BIGINT` | `FK → voyage.id, NOT NULL` | 親航海 ID |
| `departure_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 出発地（UN/LOCODE） |
| `arrival_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 到着地（UN/LOCODE） |
| `departure_date` | `TIMESTAMP` | `NOT NULL` | 出発日時 |
| `arrival_date` | `TIMESTAMP` | `NOT NULL` | 到着日時 |
| `seq_number` | `INTEGER` | `NOT NULL` | 区間順序（1 始まり） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `selected_route`（確定経路）

> **注記（IT3 追加）**: 経路設計者が経路候補から選択・確定した経路（US09）を予約番号に紐づけて永続化する。経路候補（算出結果）は一時データのため永続化せず、確定経路のみを保持する。`voyage`/`carrier_movement` と同じ規約（単数形・サロゲート PK + 業務キー UK・子は `id` 参照）に従う。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `VARCHAR(20)` | `UK, NOT NULL` | 予約 ID（1 予約に 1 確定経路） |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'SELECTED'` | 経路状態（`SELECTED`） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `selected_route_leg`（確定経路区間）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `selected_route_id` | `BIGINT` | `FK → selected_route.id, NOT NULL` | 親確定経路 ID（CASCADE 削除） |
| `voyage_number` | `VARCHAR(20)` | `NOT NULL` | 区間で使用する航海番号 |
| `load_location_unlocode` | `VARCHAR(5)` | `NOT NULL` | 積込地（UN/LOCODE） |
| `unload_location_unlocode` | `VARCHAR(5)` | `NOT NULL` | 荷降地（UN/LOCODE） |
| `load_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 積込日時 |
| `unload_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 荷降日時 |
| `seq_number` | `INTEGER` | `NOT NULL` | 区間順序（1 始まり） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `notification`（通知送信記録・IT4）

予約ライフサイクルの節目（経路設計依頼 US06・荷主への経路通知 US12・追跡番号発行依頼／キャンセル US13）で送信した通知を記録する。本 IT では「送信＝記録」に限定し、実配信（メール/SMS）はスコープ外。コンテキスト間参照（`booking_id`）は BC 独立方針に従い DB 外部キー制約を設けない。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `VARCHAR(20)` | `NOT NULL` | 対象予約 ID（業務キー参照・FK なし） |
| `notification_type` | `VARCHAR(30)` | `NOT NULL` | 通知種別（`ROUTE_DESIGN_REQUESTED`/`ROUTE_NOTIFIED_TO_SHIPPER`/`TRACKING_ISSUE_REQUESTED`/`BOOKING_CANCELLED`） |
| `recipient_role` | `VARCHAR(50)` | `NOT NULL` | 受信者ロール（`ROLE_*`） |
| `recipient_email` | `VARCHAR(200)` | `NOT NULL` | 受信者メールアドレス |
| `subject` | `VARCHAR(200)` | `NOT NULL` | 件名 |
| `body` | `TEXT` | `NOT NULL` | 本文 |
| `sent_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | 送信（記録）日時 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

インデックス: `idx_notification_booking_id (booking_id)`。

---

### `tracking_activity`（追跡レコード）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_number` | `VARCHAR(20)` | `UK, NOT NULL` | 追跡番号（業務キー） |
| `booking_id` | `VARCHAR(20)` | `NOT NULL` | 予約 ID（参照整合性は書き込み側で保証） |
| `transport_status` | `VARCHAR(30)` | `NOT NULL` | 輸送状態（TransportStatus 列挙値） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `tracking_handling_event`（追跡イベント）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_id` | `BIGINT` | `FK → tracking_activity.id, NOT NULL` | 親追跡レコード ID |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | 荷役タイプ（HandlingType 列挙値） |
| `event_time` | `TIMESTAMP` | `NOT NULL` | イベント発生日時 |
| `location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode` | イベント発生場所（UN/LOCODE） |
| `voyage_number` | `VARCHAR(20)` | | 関連する航海番号 |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `tracking_exception_event`（追跡例外イベント）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_id` | `BIGINT` | `FK → tracking_activity.id, NOT NULL` | 親追跡レコード ID |
| `exception_type` | `VARCHAR(50)` | `NOT NULL` | 例外種別（例: `CUSTOMS_HOLD`, `DAMAGE`, `DELAY`） |
| `occurred_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 例外発生日時 |
| `escalation_flag` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | エスカレーション判定フラグ（US15 紛失時） |
| `description` | `VARCHAR(500)` | | 例外内容の詳細 |
| `resolved_at` | `TIMESTAMP WITH TIME ZONE` | | 解決日時（NULL = 未解決） |
| `resolution_notes` | `TEXT` | | 対応内容メモ |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `handling_activity`（荷役作業記録）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `VARCHAR(20)` | `NOT NULL` | 予約 ID（参照整合性は書き込み側で保証） |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | 荷役タイプ（RECEIVE / LOAD / UNLOAD / CUSTOMS / CLAIM） |
| `event_completion_time` | `TIMESTAMP` | `NOT NULL` | 荷役完了日時 |
| `location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 作業場所（UN/LOCODE） |
| `voyage_number` | `VARCHAR(20)` | | 関連する航海番号（LOAD / UNLOAD 時に設定） |
| `operator_name` | `VARCHAR(200)` | | 作業員名 |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `customs_declaration`（税関申告）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `handling_activity_id` | `BIGINT` | `FK → handling_activity.id, NOT NULL` | 関連荷役作業 ID |
| `declaration_number` | `VARCHAR(50)` | `UK, NOT NULL` | 申告番号（業務キー） |
| `declared_at` | `TIMESTAMP` | `NOT NULL` | 申告日時 |
| `status` | `VARCHAR(30)` | `NOT NULL` | 申告状態（例: `PENDING`, `CLEARED`, `HELD`） |
| `cleared_at` | `TIMESTAMP` | | 通関完了日時（NULL = 未完了） |
| `remarks` | `VARCHAR(500)` | | 備考・メモ |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `invoice`（精算書）

> **金額型の統一（IT8 実装）**: 金額カラム（`charge_total_value`・`total_amount_value`・明細/支払の `amount_value`）は `freight_charge`・`Money`（`Decimal`）と整合させ **`NUMERIC(15,2)`** で実装する（マイグレーション `20261014000001_it8_invoice_payment.sql`）。下表の `INTEGER` 表記は初期設計の名残であり実装は `NUMERIC(15,2)`。US23 で確定料金（割引後・税抜）`charge_total_value` を追加。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（IDENTITY） |
| `invoice_number` | `VARCHAR(30)` | `UK, NOT NULL` | 精算書番号（業務キー・`INV-`＋24 桁） |
| `booking_id` | `VARCHAR(20)` | `UK, NOT NULL` | 予約 ID（UNIQUE 制約で二重請求を防止） |
| `charge_total_value` | `NUMERIC(15,2)` | `NOT NULL` | 確定料金（割引後・税抜・IT8 追加） |
| `charge_total_currency` | `VARCHAR(3)` | `NOT NULL, DEFAULT 'JPY'` | 確定料金の通貨コード（ISO 4217・IT8 追加） |
| `tax_rate` | `NUMERIC(5,4)` | `NOT NULL, DEFAULT 0.1000` | 消費税率（デフォルト 10%） |
| `tax_amount` | `NUMERIC(15,2)` | `NOT NULL, DEFAULT 0` | 消費税額 |
| `total_amount_value` | `NUMERIC(15,2)` | `NOT NULL` | 請求金額（税込・確定料金＋消費税） |
| `total_amount_currency` | `VARCHAR(3)` | `NOT NULL, DEFAULT 'JPY'` | 通貨コード（ISO 4217） |
| `payment_status` | `VARCHAR(30)` | `NOT NULL, DEFAULT 'PENDING'` | 支払状態（`PENDING` / `CONFIRMED` / `OVERDUE` / `REFUNDED`） |
| `issued_at` | `TIMESTAMP WITH TIME ZONE` | | 発行日時 |
| `due_date` | `DATE` | | 支払期日 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

> 割引額は確定料金（`charge_total_value`）に反映済み（`freight_charge` で割引適用後の total を保持）のため、`invoice` に discount 列は持たない（IT8 マイグレーション準拠）。

---

### `invoice_line_item`（精算明細）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `invoice_id` | `BIGINT` | `FK → invoice.id, NOT NULL` | 親精算書 ID |
| `description` | `VARCHAR(200)` | `NOT NULL` | 明細項目説明 |
| `amount_value` | `NUMERIC(15,2)` | `NOT NULL` | 明細金額 |
| `amount_currency` | `VARCHAR(3)` | `NOT NULL, DEFAULT 'JPY'` | 通貨コード（ISO 4217） |
| `seq_number` | `INTEGER` | `NOT NULL` | 明細順序（1 始まり） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |

---

### `payment`（支払記録）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `invoice_id` | `BIGINT` | `FK → invoice.id, NOT NULL` | 親精算書 ID |
| `paid_amount_value` | `NUMERIC(15,2)` | `NOT NULL` | 支払金額 |
| `paid_amount_currency` | `VARCHAR(3)` | `NOT NULL, DEFAULT 'JPY'` | 通貨コード（ISO 4217） |
| `paid_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 支払日時 |
| `payment_method` | `VARCHAR(30)` | `NOT NULL` | 支払方法（例: `BANK_TRANSFER`, `CREDIT_CARD`） |
| `transaction_reference` | `VARCHAR(100)` | | 取引参照番号（外部決済システムの ID） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |

---

### `users`（ユーザー）

axum の認証層（ログイン検証・トークン発行）が参照するユーザー認証テーブル。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `username` | `VARCHAR(50)` | `UK, NOT NULL` | ログイン名 |
| `email` | `VARCHAR(200)` | `UK, NOT NULL` | メールアドレス |
| `password` | `VARCHAR(255)` | `NOT NULL` | パスワード（bcrypt / argon2 ハッシュ） |
| `enabled` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | アカウント有効フラグ |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |

#### DDL

```sql
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(200) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,  -- bcrypt / argon2 ハッシュ
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

---

### `user_roles`（ユーザーロール）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `user_id` | `BIGINT` | `PK, FK → users.id, NOT NULL` | 親ユーザー ID |
| `role` | `VARCHAR(50)` | `PK, NOT NULL` | ロール名（`ROLE_ADMIN` / `ROLE_OPERATOR` / `ROLE_SHIPPER` 等） |

#### DDL

```sql
CREATE TABLE user_roles (
    user_id  BIGINT      NOT NULL REFERENCES users(id),
    role     VARCHAR(50) NOT NULL,  -- ROLE_ADMIN / ROLE_OPERATOR / ROLE_SHIPPER 等
    PRIMARY KEY (user_id, role)
);
```

---

### `estimate`（見積）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（`GENERATED BY DEFAULT AS IDENTITY`） |
| `estimate_id` | `UUID` | `UK, NOT NULL` | 見積 ID（業務キー。`uuid::Uuid`） |
| `origin_unlocode` | `VARCHAR(5)` | `NOT NULL` | 出発地（UN/LOCODE） |
| `destination_unlocode` | `VARCHAR(5)` | `NOT NULL` | 仕向地（UN/LOCODE） |
| `arrival_deadline` | `DATE` | `NOT NULL` | 到着期限 |
| `cargo_type` | `VARCHAR(30)` | `NOT NULL` | 貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`） |
| `weight_kg` | `NUMERIC(10,3)` | `NOT NULL` | 重量（kg） |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'CREATED'` | 見積状態（`CREATED` / `EXPIRED`） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### DDL

```sql
CREATE TABLE estimate (
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    estimate_id           UUID NOT NULL UNIQUE,
    origin_unlocode       VARCHAR(5) NOT NULL,
    destination_unlocode  VARCHAR(5) NOT NULL,
    arrival_deadline      DATE NOT NULL,
    cargo_type            VARCHAR(30) NOT NULL,
    weight_kg             NUMERIC(10, 3) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

---

### `route_candidate`（ルート候補）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（`GENERATED BY DEFAULT AS IDENTITY`） |
| `estimate_id` | `BIGINT` | `FK → estimate.id, NOT NULL` | 親見積 ID（CASCADE 削除） |
| `voyage_number` | `VARCHAR(20)` | `NOT NULL` | 航海番号 |
| `transit_port` | `VARCHAR(5)` | | 経由港（UN/LOCODE、オプション） |
| `transit_days` | `INT` | `NOT NULL` | 輸送日数 |
| `estimated_cost` | `NUMERIC(12,2)` | `NOT NULL` | 見積コスト |
| `rank` | `INT` | `NOT NULL, DEFAULT 0` | ルート候補の優先順位 |

#### DDL

```sql
CREATE TABLE route_candidate (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    estimate_id     BIGINT NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number   VARCHAR(20) NOT NULL,
    transit_port    VARCHAR(5),
    transit_days    INT NOT NULL,
    estimated_cost  NUMERIC(12, 2) NOT NULL,
    rank            INT NOT NULL DEFAULT 0
);
```

---

## 設計上の判断

### 1. サロゲートキーと業務キーの併用

**判断**: 全テーブルに `BIGSERIAL` のサロゲートキー（`id`）を設け、業務上の識別子（`booking_id`、`voyage_number`、`unlocode` 等）には `UNIQUE` 制約を付与する。

**根拠**: 外部キー参照を `BIGINT` に統一することでインデックス効率が向上する。業務キーはドメインモデルの一部（newtype 値オブジェクト）であり、別途管理することで業務ルールの変更に対応しやすい。

---

### 2. `location` テーブルへの参照方式

**判断**: 参考実装では `VARCHAR` で場所 ID を文字列管理していたが、本設計では `location.unlocode` を外部キーとして参照する。

**根拠**: UN/LOCODE は国際標準の 5 文字コードであり、それ自体が意味を持つ自然キーである。文字列参照でも JOIN 効率は許容範囲内であり、可読性が高まる。

---

### 3. 金額の表現（`INTEGER` + `VARCHAR(3)`）

**判断**: 金額を `INTEGER`（最小通貨単位）と `VARCHAR(3)`（ISO 4217 通貨コード）の 2 カラムで表現する。`NUMERIC` / `DECIMAL` は使用しない。

**根拠**: 浮動小数点演算による精度誤差を排除するため、円・セントなど最小通貨単位で整数管理する。複数通貨対応のため通貨コードを常に付随させる。これはドメインモデルの `MoneyAmount` 値オブジェクト（Rust では `struct MoneyAmount { value: i32, currency: Currency }`）に対応する。税率・税額など比率演算が必要な箇所のみ `NUMERIC`（`rust_decimal::Decimal`）を用いる。

---

### 4. 列挙値のカラム型（`VARCHAR(30)`）

**判断**: `BookingStatus`、`TransportStatus`、`HandlingType` 等の列挙型カラムは `VARCHAR(30)` で表現し、PostgreSQL の `ENUM` 型は使用しない。

**根拠**: PostgreSQL `ENUM` 型は値の追加・変更にスキーマ ALTER が必要でマイグレーション時のリスクが高い。`VARCHAR` ならば sqlx マイグレーションで CHECK 制約を追加・変更するだけで済む。Rust 側では `#[derive(sqlx::Type)]` を付与した `enum` に文字列としてマッピングするため、型安全性はアプリケーション層で確保できる。

```rust
#[derive(Debug, Clone, PartialEq, Eq, sqlx::Type)]
#[sqlx(type_name = "VARCHAR", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BookingStatus {
    Preliminary,
    Confirmed,
    Routed,
    InTransit,
    Delivered,
    Settled,
    Cancelled,
}
```

---

### 5. コンテキスト間の参照整合性

**判断**: 異なるコンテキスト間（例: `handling_activity.booking_id` → `cargo.booking_id`）には DB 外部キー制約を設けない。コンテキスト内の参照（例: `leg.cargo_id` → `cargo.id`）には外部キー制約を設ける。

**根拠**: DDD の境界付けられたコンテキスト間はイベント連携を前提とする疎結合設計であり、DB 外部キーによる強結合は将来のサービス分割を妨げる。整合性はアプリケーション層（ACL ポート・ドメインイベント）で保証する。

---

### 6. `Billing Context` の新規設計

**判断**: 参考実装（Jakarta EE）には `Billing Context` が存在しなかったが、本設計では `invoice`・`invoice_line_item`・`payment` の 3 テーブルを新規追加する。

**根拠**: ドメインモデル分析で識別した `SETTLED`（BookingStatus）と `Invoice` エンティティを実現するために必要。経理担当者のユースケース（精算書生成・支払確認）を支える永続化構造として設計した。

---

### 7. 監査カラムの全テーブル付与

**判断**: `created_at`・`updated_at` を全テーブルに `NOT NULL DEFAULT NOW()` で付与する。`updated_at` の更新は sqlx リポジトリ実装側で `NOW()` をセットする（`UPDATE ... SET updated_at = NOW()`）。

**根拠**: 国際貨物輸送は規制上の監査要件が高く、全レコードの作成・更新タイムスタンプが必要。PostgreSQL のトリガーで自動更新する方法もあるが、SQL の可視性（コンパイル時検証対象に含める）を優先してアプリケーション側で制御する。

---

### 8. sqlx コンパイル時検証との整合

**判断**: リポジトリ実装では原則 `sqlx::query_as!` / `sqlx::query!` マクロを使用し、SQL をコンパイル時に検証する。

**根拠**: スキーマとコードの不整合をビルド時に検出できるため、マイグレーションとコードの乖離という典型的な障害を排除できる。CI ではオフラインモード（`cargo sqlx prepare` による `.sqlx/` キャッシュ）を利用する。

```rust
let row = sqlx::query_as!(
    CargoRow,
    r#"SELECT id, booking_id, shipper_id, booking_status, cargo_type,
              weight_kg, declared_value, created_at, updated_at
       FROM cargo WHERE booking_id = $1"#,
    booking_id.as_str()
)
.fetch_optional(&pool)
.await?;
```

---

## sqlx マイグレーション方針

### ファイル命名規則

sqlx migrate はタイムスタンププレフィックス付きの SQL ファイルを `migrations/` ディレクトリで管理する。

```
migrations/
  20260706000001_init.sql            # 初期スキーマ全テーブル作成
  20260706000002_seed_locations.sql  # 初期 UN/LOCODE マスタデータ
  20260706000003_add_xxx.sql         # 機能追加に伴うスキーマ変更
```

マイグレーションの作成・適用は sqlx-cli で行う。

```bash
sqlx migrate add init          # migrations/<timestamp>_init.sql を生成
sqlx migrate run               # DATABASE_URL の DB に未適用分を適用
sqlx migrate info              # 適用状況の確認
cargo sqlx prepare             # オフライン検証用 .sqlx/ キャッシュ生成
```

### マイグレーションルール

- マイグレーションはタイムスタンプ順に適用される。適用順が入れ替わらないよう `sqlx migrate add` で生成する
- 既存マイグレーションファイルの編集は禁止（sqlx はチェックサムを `_sqlx_migrations` テーブルで検証する）
- ロールバックが必要な場合は `sqlx migrate add -r <name>` で生成する `.up.sql` / `.down.sql` のペア（reversible migration）で対応する
- 本番・テストとも PostgreSQL 16 を使用するため、PostgreSQL 固有構文（`BIGSERIAL`、`TIMESTAMP WITH TIME ZONE` 等）をそのまま使用してよい
- スキーマ変更後は必ず `cargo sqlx prepare` を再実行し、`.sqlx/` キャッシュをコミットする

### 初期マイグレーション（`init.sql`）の構成イメージ

```sql
-- Shared Domain
CREATE TABLE location ( ... );

-- Security Context
CREATE TABLE users ( ... );
CREATE TABLE user_roles ( ... );

-- Booking Context
CREATE TABLE shipper ( ... );
CREATE TABLE cargo ( ... );   -- shipper_id FK あり
CREATE TABLE leg ( ... );

-- Routing Context
CREATE TABLE voyage ( ... );
CREATE TABLE carrier_movement ( ... );

-- Tracking Context
CREATE TABLE tracking_activity ( ... );
CREATE TABLE tracking_handling_event ( ... );
CREATE TABLE tracking_exception_event ( ... );  -- escalation_flag / resolution_notes あり

-- Handling Context
CREATE TABLE handling_activity ( ... );
CREATE TABLE customs_declaration ( ... );

-- Billing Context
CREATE TABLE invoice ( ... );  -- tax_rate / tax_amount / booking_id UNIQUE あり
CREATE TABLE invoice_line_item ( ... );
CREATE TABLE payment ( ... );

-- Estimation Context（後続マイグレーション add_estimate.sql）
CREATE TABLE estimate ( ... );       -- estimate_id UUID UNIQUE あり
CREATE TABLE route_candidate ( ... ); -- estimate FK (CASCADE 削除) あり
```
