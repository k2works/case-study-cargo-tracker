---
title: データモデル設計
description: 国際貨物輸送管理システムのデータモデル設計。概念データモデル・論理データモデル・テーブル定義を含む。
published: true
date: 2026-03-31
tags: design,data-model
---

# データモデル設計 - 国際貨物輸送管理システム

## 概要

本ドキュメントは、国際貨物輸送管理システムの永続化層データモデルを定義する。
ドメインモデル分析で識別した 6 つの境界付けられたコンテキスト（Booking / Shipper / Routing / Tracking / Billing / Estimation）と共有ドメイン（Shared Domain）、および支援サブドメインである Security に対応する 20 テーブルを設計する。荷役・通関申告のテーブルは Tracking Context に属する（ADR-002。**BC を統合してもテーブルは分割したまま維持する**）。
`shipper`（荷主）テーブルと、Spring Security 用の `users` / `user_roles` テーブルを含む。

> **本ドキュメントの ER 図は「設計」である。** 実際に Flyway が構築したスキーマの ER 図は
> jig-erd で生成できる（`./gradlew jigErd`）。**設計と実装の乖離は、図を目視で見比べるのではなく
> 生成物との差分で検出する。** 手で図を更新し続ける運用は、マイグレーションを追加したのに
> 図だけ古いという状態を必ず生む。
>
> なお jig-erd が扱うのはテーブルと外部キーの関連のみである。PK・データ型・制約の正典は本ドキュメントである。

### 設計方針

- **DB**: PostgreSQL 16.x（本番・Repository テスト・E2E）、H2 PostgreSQL 互換モード（ローカル起動のみ — ADR-003）
- **ORM**: MyBatis（XML マッパー）
- **マイグレーション**: Flyway。`db/migration/common`（両 DB 共通）と `db/migration/{vendor}`（ベンダー固有）に分離する
- **ID 戦略**: サロゲートキー（`BIGSERIAL`）+ 業務キー（`VARCHAR`）の併用。例外として `shipper.id` と `cargo.booking_id` / `cargo.shipper_id` は `UUID`（V3/V4 マイグレーション）
- **命名規則**: スネークケース（PostgreSQL 慣習）
- **監査カラム**: 全テーブルに `created_at` / `updated_at` を付与

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
    * email : VARCHAR(200) <<UK>>
    * address_country : CHAR(2)
    * address_postal_code : VARCHAR(20)
    * address_region : VARCHAR(100)
    * address_city : VARCHAR(100)
    address_street : VARCHAR(200)
  }

  entity "cargo\n（貨物）" as cargo {
    * id : BIGINT <<PK>>
    --
    * booking_id : UUID <<UK>>
    * shipper_id : UUID <<FK>>
    * booking_status : VARCHAR(30)
    * transport_status : VARCHAR(30)
    * routing_status : VARCHAR(30)
    * cargo_type : VARCHAR(20)
    * weight_kg : NUMERIC(10,3)
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
    * load_time : TIMESTAMPTZ
    * unload_time : TIMESTAMPTZ
  }
}

package "Routing Context" #lightgreen {
  entity "booking_route_proposal\n（経路提案）" as booking_route_proposal {
    * id : BIGINT <<PK>>
    --
    * booking_id : UUID <<UK>>
    * origin_unlocode : VARCHAR(5) <<FK>>
    * destination_unlocode : VARCHAR(5) <<FK>>
    * arrival_deadline : DATE
    * candidate_count : INT
    selected_route_id : BIGINT <<FK>>
  }

  entity "proposed_route\n（経路候補）" as proposed_route {
    * id : BIGINT <<PK>>
    --
    * proposal_id : BIGINT <<FK>>
    * voyage_number : VARCHAR(20)
    * transit_days : INT
    * estimated_cost_value : INTEGER
    * capacity_available : BOOLEAN
    * deadline_satisfied : BOOLEAN
  }

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
    * departure_date : TIMESTAMPTZ
    * arrival_date : TIMESTAMPTZ
  }
}

package "Tracking Context" #lightyellow {
  entity "tracking_activity\n（追跡レコード）" as tracking_activity {
    * id : BIGINT <<PK>>
    --
    * tracking_number : VARCHAR(20) <<UK>>
    * booking_id : UUID
    * transport_status : VARCHAR(30)
  }

  entity "tracking_handling_event\n（追跡イベント）" as tracking_handling_event {
    * id : BIGINT <<PK>>
    --
    * tracking_id : BIGINT <<FK>>
    * event_type : VARCHAR(30)
    * event_time : TIMESTAMPTZ
    * location_unlocode : VARCHAR(5) <<FK>>
    * voyage_number : VARCHAR(20)
  }

  entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
    * id : BIGINT <<PK>>
    --
    * tracking_id : BIGINT <<FK>>
    * exception_type : VARCHAR(50)
    * occurred_at : TIMESTAMPTZ
    * escalation_flag : BOOLEAN
    * status_before : VARCHAR(30)
    description : VARCHAR(500)
    resolved_at : TIMESTAMPTZ
    resolution_notes : TEXT
  }
}

package "Tracking Context / Handling モジュール" #lightcoral {
  entity "handling_activity\n（荷役作業記録）" as handling_activity {
    * id : BIGINT <<PK>>
    --
    * booking_id : UUID
    * event_type : VARCHAR(30)
    * event_completion_time : TIMESTAMPTZ
    * location_unlocode : VARCHAR(5) <<FK>>
    * voyage_number : VARCHAR(20)
  }

  entity "customs_declaration\n（税関申告）" as customs_declaration {
    * id : BIGINT <<PK>>
    --
    * handling_activity_id : BIGINT <<FK>>
    * declaration_number : VARCHAR(50) <<UK>>
    * declared_at : TIMESTAMPTZ
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
    * estimated_cost_value : INTEGER
    * estimated_cost_currency : VARCHAR(3)
    * priority : INT
  }
}

package "Billing Context" #lightpink {
  entity "invoice\n（精算書）" as invoice {
    * id : BIGINT <<PK>>
    --
    * invoice_number : VARCHAR(30) <<UK>>
    * booking_id : UUID <<UK>>
    * total_amount_value : INTEGER
    * total_amount_currency : VARCHAR(3)
    * tax_rate : NUMERIC(5,4)
    * tax_amount_value : INTEGER
    * tax_amount_currency : VARCHAR(3)
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
    * paid_at : TIMESTAMPTZ
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
booking_route_proposal ||--o{ proposed_route : "候補を持つ"
booking_route_proposal }o--|| location : "出発地"
booking_route_proposal }o--|| location : "目的地"
voyage ||--o{ carrier_movement : "運送区間を持つ"
carrier_movement }o--|| location : "出発地"
carrier_movement }o--|| location : "到着地"

' Tracking Context relations
tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"
tracking_handling_event }o--o| location : "発生場所"

' Handling モジュール relations
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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "cargo\n（貨物）" as cargo {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : UUID <<UK, NOT NULL>>
  * shipper_id : UUID <<FK, NOT NULL>>
  * booking_status : VARCHAR(30) <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
  * routing_status : VARCHAR(30) <<NOT NULL>>
  * cargo_type : VARCHAR(20) <<NOT NULL, DEFAULT 'GENERAL'>>
  * weight_kg : NUMERIC(10,3) <<NOT NULL>>
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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "leg\n（輸送区間）" as leg {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * cargo_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<FK, NOT NULL>>
  * load_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * unload_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  load_time : TIMESTAMPTZ
  unload_time : TIMESTAMPTZ
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "carrier_movement\n（運送区間）" as carrier_movement {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * voyage_id : BIGINT <<FK, NOT NULL>>
  * departure_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * arrival_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * departure_date : TIMESTAMPTZ <<NOT NULL>>
  * arrival_date : TIMESTAMPTZ <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

voyage ||--o{ carrier_movement : "運送区間を持つ"

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
  * booking_id : UUID <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "tracking_handling_event\n（追跡イベント）" as tracking_handling_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_time : TIMESTAMPTZ <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK>>
  voyage_number : VARCHAR(20)
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * exception_type : VARCHAR(50) <<NOT NULL>>
  * occurred_at : TIMESTAMPTZ <<NOT NULL>>
  * escalation_flag : BOOLEAN <<NOT NULL, DEFAULT FALSE>>
  * status_before : VARCHAR(30) <<NOT NULL>>
  description : VARCHAR(500)
  resolved_at : TIMESTAMPTZ
  resolution_notes : TEXT
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"

@enduml
```

---

### Tracking Context / Handling モジュール

荷役作業の実績と税関申告を管理する。`handling_activity` が集約ルート。

```plantuml
@startuml
title 論理データモデル - Tracking Context / Handling モジュール

entity "handling_activity\n（荷役作業記録）" as handling_activity {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : UUID <<NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_completion_time : TIMESTAMPTZ <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  voyage_number : VARCHAR(20)
  operator_name : VARCHAR(200)
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "customs_declaration\n（税関申告）" as customs_declaration {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * handling_activity_id : BIGINT <<FK, NOT NULL>>
  * declaration_number : VARCHAR(50) <<UK, NOT NULL>>
  * declared_at : TIMESTAMPTZ <<NOT NULL>>
  * status : VARCHAR(30) <<NOT NULL>>
  cleared_at : TIMESTAMPTZ
  remarks : VARCHAR(500)
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
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
  * booking_id : UUID <<UK, NOT NULL>>
  * total_amount_value : INTEGER <<NOT NULL>>
  * total_amount_currency : VARCHAR(3) <<NOT NULL>>
  * tax_rate : NUMERIC(5,4) <<NOT NULL, DEFAULT 0.1000>>
  * tax_amount_value : INTEGER
    * tax_amount_currency : VARCHAR(3) <<NOT NULL, DEFAULT 0>>
  * payment_status : VARCHAR(30) <<NOT NULL>>
  issued_at : TIMESTAMPTZ
  due_date : DATE
  discount_amount_value : INTEGER
  discount_amount_currency : VARCHAR(3)
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "invoice_line_item\n（精算明細）" as invoice_line_item {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * description : VARCHAR(200) <<NOT NULL>>
  * amount_value : INTEGER <<NOT NULL>>
  * amount_currency : VARCHAR(3) <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "payment\n（支払記録）" as payment {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * paid_amount_value : INTEGER <<NOT NULL>>
  * paid_amount_currency : VARCHAR(3) <<NOT NULL>>
  * paid_at : TIMESTAMPTZ <<NOT NULL>>
  * payment_method : VARCHAR(30) <<NOT NULL>>
  transaction_reference : VARCHAR(100)
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

invoice ||--o{ invoice_line_item : "明細を持つ"
invoice ||--o{ payment : "支払を持つ"

@enduml
```

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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
}

entity "route_candidate\n（ルート候補）" as route_candidate {
  * id : BIGINT <<PK, GENERATED BY DEFAULT AS IDENTITY>>
  --
  * estimate_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<NOT NULL>>
  transit_port : VARCHAR(5)
  * transit_days : INT <<NOT NULL>>
  * estimated_cost_value : INTEGER
    * estimated_cost_currency : VARCHAR(3) <<NOT NULL>>
  * priority : INT <<NOT NULL, DEFAULT 0>>
}

estimate ||--o{ route_candidate : "ルート候補を持つ"

@enduml
```

---

### Security Context

Spring Security の `UserDetailsService` が利用するユーザー認証・認可テーブル。

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
  * created_at : TIMESTAMPTZ <<NOT NULL, DEFAULT NOW()>>
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
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `shipper`（荷主）

> **注記**: 旧設計で `cargo` テーブルに存在した `shipper_name`・`shipper_email` カラムは本テーブルへの正規化に伴い削除した。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PK, NOT NULL` | サロゲートキー（UUID。アプリケーション側で採番） |
| `shipper_code` | `VARCHAR(20)` | `UK, NOT NULL` | 荷主コード（業務キー。SHP-XXXXXX 形式） |
| `shipper_type` | `VARCHAR(20)` | `NOT NULL` | 荷主種別（`INDIVIDUAL` / `CORPORATE`） |
| `name` | `VARCHAR(200)` | `NOT NULL` | 荷主名称 |
| `email` | `VARCHAR(200)` | **`UK`**, `NOT NULL` | メールアドレス。US02 の受入基準「同一メールアドレスが既に登録されている場合はエラー」を **DB で保証する** |
| `phone` | `VARCHAR(50)` | | 電話番号 |
| `address_country` | `CHAR(2)` | `NOT NULL` | 国コード（ISO 3166-1 alpha-2） |
| `address_postal_code` | `VARCHAR(20)` | `NOT NULL` | 郵便番号 |
| `address_region` | `VARCHAR(100)` | `NOT NULL` | 都道府県 / 州 |
| `address_city` | `VARCHAR(100)` | `NOT NULL` | 市区町村 |
| `address_street` | `VARCHAR(200)` | | 番地・建物名 |
| `contract_number` | `VARCHAR(50)` | | 契約番号（法人のみ。NULLable） |
| `discount_rate` | `NUMERIC(5,4)` | `NOT NULL, DEFAULT 0.0000` | **契約**割引率（0.0000〜0.3000、上限 30%）。US22 で `ShipperDiscountPort` から参照される |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

> **住所カラムはドメインの `Address` 値オブジェクトに対応する。** US02 の受入基準は住所の入力を求めており、`domain-model.md` にも `Address` 値オブジェクトが定義されているが、旧版のテーブルには住所を保持する列が 1 つも無かった。**受入基準を満たせないスキーマは、実装時に必ず作り直しになる。**
>
> **メールアドレスの一意性はアプリケーションだけで担保しない。** 画面の非同期チェックは同時登録の競合に対して無力であり、DB の UNIQUE 制約が最後の防波堤になる。

#### DDL

```sql
CREATE TABLE shipper (
    id                  UUID PRIMARY KEY,
    shipper_code        VARCHAR(20)  NOT NULL UNIQUE,  -- SHP-XXXXXX 形式
    shipper_type        VARCHAR(20)  NOT NULL,         -- INDIVIDUAL / CORPORATE
    name                VARCHAR(200) NOT NULL,
    email               VARCHAR(200) NOT NULL UNIQUE,  -- US02 の重複チェックを DB で保証
    phone               VARCHAR(50),
    address_country     CHAR(2)      NOT NULL,         -- ISO 3166-1 alpha-2
    address_postal_code VARCHAR(20)  NOT NULL,
    address_region      VARCHAR(100) NOT NULL,
    address_city        VARCHAR(100) NOT NULL,
    address_street      VARCHAR(200),
    contract_number     VARCHAR(50),                   -- 法人のみ（NULLable）
    discount_rate       NUMERIC(5,4) NOT NULL DEFAULT 0.0000,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_shipper_discount_rate
        CHECK (discount_rate >= 0.0000 AND discount_rate <= 0.3000),
    CONSTRAINT chk_shipper_corporate_contract
        CHECK (shipper_type <> 'CORPORATE' OR contract_number IS NOT NULL)
);
```

---

### `cargo`（貨物）

> **注記**: `shipper_name`・`shipper_email` カラムは削除し、`shipper_id`（FK → `shipper.id`）による参照に変更した。
>
> **IT2 実装状況**: IT2 完了時点（2026-04-06）で V4〜V7 マイグレーションが適用済み。
> 将来フェーズで追加予定のカラム（`transport_status`・`routing_status`・`booking_amount_*`・`consignee_*`・`tracking_number` 等）は下表の「将来追加予定」節に記載する。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（`BIGINT GENERATED BY DEFAULT AS IDENTITY`） |
| `booking_id` | `UUID` | `UK, NOT NULL` | 予約 ID（業務キー） |
| `shipper_id` | `UUID` | `FK → shipper.id, NOT NULL` | 荷主 ID |
| `cargo_type` | `VARCHAR(30)` | `NOT NULL` | 貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`） |
| `weight` | `NUMERIC(10,3)` | `NOT NULL, > 0` | 重量（kg） |
| `origin_unlocode` | `VARCHAR(5)` | `NOT NULL` | 出発地（RouteSpecification） |
| `destination_unlocode` | `VARCHAR(5)` | `NOT NULL` | 仕向地（RouteSpecification） |
| `arrival_deadline` | `DATE` | `NOT NULL` | 到着期限（RouteSpecification） |
| `booking_status` | `VARCHAR(30)` | `NOT NULL, DEFAULT 'PRELIMINARY'` | 予約状態（BookingStatus 列挙値） |
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
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### 将来追加予定カラム（IT4+）

| カラム名 | データ型 | 説明 | 追加フェーズ |
| :--- | :--- | :--- | :--- |
| `transport_status` | `VARCHAR(30)` | 輸送状態（TransportStatus 列挙値） | Tracking Context 実装時 |
| `routing_status` | `VARCHAR(30)` | 経路決定状態（ROUTED / MISROUTED / NOT_ROUTED） | Routing Context 実装時 |
| `booking_amount_value` | `INTEGER` | 予約金額（最小通貨単位） | Billing Context 実装時 |
| `booking_amount_currency` | `VARCHAR(3)` | 通貨コード（ISO 4217） | Billing Context 実装時 |
| `consignee_name` | `VARCHAR(200)` | 荷受人名 | 荷受人管理実装時 |
| `consignee_email` | `VARCHAR(200)` | 荷受人メールアドレス | 荷受人管理実装時 |
| `tracking_number` | `VARCHAR(20)` | 追跡番号（発行後に設定） | Tracking Context 実装時 |
| `next_expected_*` | 各種 | 次の予定荷役情報 | Tracking Context 実装時 |
| `last_handling_event_*` | 各種 | 最後の荷役イベント情報 | Handling モジュール実装時 |

---

### `leg`（輸送区間）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `cargo_id` | `BIGINT` | `FK → cargo.id, NOT NULL` | 親貨物 ID |
| `voyage_number` | `VARCHAR(20)` | `FK → voyage.voyage_number, NOT NULL` | 航海番号 |
| `load_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 積込場所（UN/LOCODE） |
| `unload_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 荷降場所（UN/LOCODE） |
| `load_time` | `TIMESTAMPTZ` | | 積込予定日時 |
| `unload_time` | `TIMESTAMPTZ` | | 荷降予定日時 |
| `seq_number` | `INTEGER` | `NOT NULL` | 区間順序（1 始まり） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `voyage`（航海）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `voyage_number` | `VARCHAR(20)` | `UK, NOT NULL` | 航海番号（業務キー） |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `booking_route_proposal`（経路提案）

予約 1 件に対して算出した経路候補の集合。**US09（選択・確定）と US10（条件変更・再算出）の置き場**であり、見積の `route_candidate` とは目的も生存期間も異なるため統合しない。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `UUID` | `UK, NOT NULL` | 予約 ID（1 予約につき 1 提案。参照整合性は書き込み側で保証） |
| `origin_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 探索条件: 出発地（誤配の再設計時は**貨物の現在地**が入る。US28） |
| `destination_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 探索条件: 目的地 |
| `arrival_deadline` | `DATE` | `NOT NULL` | 探索条件: 希望到着期限（US10 で緩められる） |
| `original_arrival_deadline` | `DATE` | `NOT NULL` | **当初**の希望期限。US10 で延長した場合の差分を荷主通知に含めるため保持する |
| `max_transit_count` | `INTEGER` | `NOT NULL, DEFAULT 2` | 探索条件: 経由回数の上限（US10 で緩められる） |
| `calculation_count` | `INTEGER` | `NOT NULL, DEFAULT 1` | 何回目の算出か（再算出のたびに加算） |
| `candidate_count` | `INTEGER` | `NOT NULL, DEFAULT 0` | 算出された候補件数。**0 は「候補ゼロ」を意味し、経路割り当て待ち一覧に表示される** |
| `selected_route_id` | `BIGINT` | `FK → proposed_route.id` | 選択・確定した候補（未確定は NULL） |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `proposed_route`（経路候補）

`booking_route_proposal` に従属する候補 1 件。**再算出時は親提案に紐づく行を全削除して入れ替える。**

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `proposal_id` | `BIGINT` | `FK → booking_route_proposal.id, NOT NULL` | 親提案 ID |
| `voyage_number` | `VARCHAR(20)` | `NOT NULL` | 航海番号 |
| `transit_ports` | `VARCHAR(200)` | | 経由港（UN/LOCODE のカンマ区切り。直行は NULL） |
| `departure_date` | `TIMESTAMPTZ` | `NOT NULL` | 出発日時 |
| `arrival_date` | `TIMESTAMPTZ` | `NOT NULL` | 到着予定日時 |
| `transit_days` | `INTEGER` | `NOT NULL` | 所要日数 |
| `estimated_cost_value` | `INTEGER` | `NOT NULL` | 費用（最小通貨単位の整数）。US08 の受入基準に含まれる |
| `estimated_cost_currency` | `VARCHAR(3)` | `NOT NULL` | 費用の通貨コード（ISO 4217） |
| `capacity_available` | `BOOLEAN` | `NOT NULL` | 空き容量の有無。**false でも一覧には残し、選択不可の理由として示す** |
| `hazardous_allowed` | `BOOLEAN` | `NOT NULL` | 危険物の取扱可否（US05 / US07 の受入基準） |
| `refrigerated_allowed` | `BOOLEAN` | `NOT NULL` | 冷凍・冷蔵の取扱可否 |
| `deadline_satisfied` | `BOOLEAN` | `NOT NULL` | 希望期限を満たすか。**判定は日付単位で行う**（`domain-model.md` ビジネスルール 2-1） |
| `priority` | `INTEGER` | `NOT NULL, DEFAULT 0` | 表示順（`rank` は SQL の予約語のため使わない） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### DDL

```sql
CREATE TABLE booking_route_proposal (
    id                        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    booking_id                UUID        NOT NULL UNIQUE,
    origin_unlocode           VARCHAR(5)  NOT NULL REFERENCES location(unlocode),
    destination_unlocode      VARCHAR(5)  NOT NULL REFERENCES location(unlocode),
    arrival_deadline          DATE        NOT NULL,
    original_arrival_deadline DATE        NOT NULL,
    max_transit_count         INTEGER     NOT NULL DEFAULT 2,
    calculation_count         INTEGER     NOT NULL DEFAULT 1,
    candidate_count           INTEGER     NOT NULL DEFAULT 0,
    selected_route_id         BIGINT,
    version                   BIGINT      NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE proposed_route (
    id                      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    proposal_id             BIGINT      NOT NULL
                            REFERENCES booking_route_proposal(id) ON DELETE CASCADE,
    voyage_number           VARCHAR(20) NOT NULL,
    transit_ports           VARCHAR(200),
    departure_date          TIMESTAMPTZ NOT NULL,
    arrival_date            TIMESTAMPTZ NOT NULL,
    transit_days            INTEGER     NOT NULL,
    estimated_cost_value    INTEGER     NOT NULL,
    estimated_cost_currency VARCHAR(3)  NOT NULL,
    capacity_available      BOOLEAN     NOT NULL,
    hazardous_allowed       BOOLEAN     NOT NULL,
    refrigerated_allowed    BOOLEAN     NOT NULL,
    deadline_satisfied      BOOLEAN     NOT NULL,
    priority                INTEGER     NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- selected_route_id の FK は proposed_route 作成後に追加する（循環参照の回避）
ALTER TABLE booking_route_proposal
    ADD CONSTRAINT fk_proposal_selected_route
    FOREIGN KEY (selected_route_id) REFERENCES proposed_route(id);

CREATE INDEX idx_proposal_booking ON booking_route_proposal (booking_id);
CREATE INDEX idx_proposed_route_proposal ON proposed_route (proposal_id, priority);
```

---

### `carrier_movement`（運送区間）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `voyage_id` | `BIGINT` | `FK → voyage.id, NOT NULL` | 親航海 ID |
| `departure_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 出発地（UN/LOCODE） |
| `arrival_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 到着地（UN/LOCODE） |
| `departure_date` | `TIMESTAMPTZ` | `NOT NULL` | 出発日時 |
| `arrival_date` | `TIMESTAMPTZ` | `NOT NULL` | 到着日時 |
| `seq_number` | `INTEGER` | `NOT NULL` | 区間順序（1 始まり） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `tracking_activity`（追跡レコード）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_number` | `VARCHAR(20)` | `UK, NOT NULL` | 追跡番号（業務キー） |
| `booking_id` | `UUID` | `NOT NULL` | 予約 ID（参照整合性は書き込み側で保証。型は `cargo.booking_id` と統一） |
| `transport_status` | `VARCHAR(30)` | `NOT NULL` | 輸送状態（TransportStatus 列挙値） |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `tracking_handling_event`（追跡イベント）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_id` | `BIGINT` | `FK → tracking_activity.id, NOT NULL` | 親追跡レコード ID |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | 荷役タイプ（HandlingType 列挙値） |
| `event_time` | `TIMESTAMPTZ` | `NOT NULL` | イベント発生日時 |
| `location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode` | イベント発生場所（UN/LOCODE） |
| `voyage_number` | `VARCHAR(20)` | | 関連する航海番号 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `tracking_exception_event`（追跡例外イベント）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `tracking_id` | `BIGINT` | `FK → tracking_activity.id, NOT NULL` | 親追跡レコード ID |
| `exception_type` | `VARCHAR(50)` | `NOT NULL` | 例外種別（例: `CUSTOMS_HOLD`, `DAMAGE`, `DELAY`） |
| `occurred_at` | `TIMESTAMPTZ` | `NOT NULL` | 例外発生日時 |
| `escalation_flag` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | エスカレーション判定フラグ（US15 紛失時） |
| `status_before` | `VARCHAR(30)` | `NOT NULL` | **例外発生直前の `TransportStatus`。** 解決時の復帰先をここから読む |
| `description` | `VARCHAR(500)` | | 例外内容の詳細 |
| `resolved_at` | `TIMESTAMPTZ` | | 解決日時（NULL = 未解決） |
| `resolution_notes` | `TEXT` | | 対応内容メモ |

> **`status_before` を永続化する理由**: `domain-model.md` と `ui_design.md` は「例外解決時に例外発生前の状態に復帰する」を不変条件としている。この列が無いと復帰先を荷役イベント履歴から**再導出**するしかなく、ユニットテストが緑でもリクエストをまたぐと誤った状態に復帰する。**発生前の状態は導出せず永続化する。**
>
> なお `DAMAGE`（破損）は「解決した = 元通り」ではない。破損の事実は貨物に残り続けて US21 の料金調整の根拠になるため、復帰と併せて破損の記録を保持する（`domain-model.md` のビジネスルールを参照）。
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `handling_activity`（荷役作業記録）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `UUID` | `NOT NULL` | 予約 ID（参照整合性は書き込み側で保証。型は `cargo.booking_id` と統一） |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | 荷役タイプ（RECEIVE / LOAD / UNLOAD / CUSTOMS / CLAIM） |
| `event_completion_time` | `TIMESTAMPTZ` | `NOT NULL` | 荷役完了日時 |
| `location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 作業場所（UN/LOCODE） |
| `voyage_number` | `VARCHAR(20)` | | 関連する航海番号（LOAD / UNLOAD 時に設定） |
| `operator_name` | `VARCHAR(200)` | | 作業員名 |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `customs_declaration`（税関申告）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `handling_activity_id` | `BIGINT` | `FK → handling_activity.id, NOT NULL` | 関連荷役作業 ID |
| `declaration_number` | `VARCHAR(50)` | `UK, NOT NULL` | 申告番号（業務キー） |
| `declared_at` | `TIMESTAMPTZ` | `NOT NULL` | 申告日時 |
| `status` | `VARCHAR(30)` | `NOT NULL` | 申告状態（例: `PENDING`, `CLEARED`, `HELD`） |
| `cleared_at` | `TIMESTAMPTZ` | | 通関完了日時（NULL = 未完了） |
| `remarks` | `VARCHAR(500)` | | 備考・メモ |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `invoice`（精算書）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `invoice_number` | `VARCHAR(30)` | `UK, NOT NULL` | 精算書番号（業務キー） |
| `booking_id` | `UUID` | `UK, NOT NULL` | 予約 ID（UNIQUE 制約で二重請求を防止。型は `cargo.booking_id` と統一） |
| `total_amount_value` | `INTEGER` | `NOT NULL` | 合計金額（最小通貨単位） |
| `total_amount_currency` | `VARCHAR(3)` | `NOT NULL` | 通貨コード（ISO 4217） |
| `tax_rate` | `NUMERIC(5,4)` | `NOT NULL, DEFAULT 0.1000` | 消費税率（デフォルト 10%） |
| `base_amount_value` | `INTEGER` | `NOT NULL` | 割引適用**前**の基本料金（最小通貨単位） |
| `base_amount_currency` | `VARCHAR(3)` | `NOT NULL` | 基本料金の通貨コード（ISO 4217） |
| `discount_rate` | `NUMERIC(5,4)` | `NOT NULL, DEFAULT 0` | 適用した割引率（0.0000〜0.3000）。US22 の受入基準「割引計算の根拠が精算書に記載される」を満たすため永続化する |
| `tax_amount_value` | `INTEGER` | `NOT NULL, DEFAULT 0` | 消費税額（最小通貨単位の整数。判断 3 に従い `NUMERIC` を使わない） |
| `tax_amount_currency` | `VARCHAR(3)` | `NOT NULL` | 消費税額の通貨コード（ISO 4217） |
| `payment_status` | `VARCHAR(30)` | `NOT NULL` | 支払状態（`PENDING` / `CONFIRMED` / `OVERDUE` / `REFUNDED`） |
| `issued_at` | `TIMESTAMPTZ` | | 発行日時 |
| `due_date` | `DATE` | | 支払期日 |
| `discount_amount_value` | `INTEGER` | | 割引金額（最小通貨単位） |
| `discount_amount_currency` | `VARCHAR(3)` | | 割引通貨コード |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `invoice_line_item`（精算明細）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `invoice_id` | `BIGINT` | `FK → invoice.id, NOT NULL` | 親精算書 ID |
| `description` | `VARCHAR(200)` | `NOT NULL` | 明細項目説明 |
| `amount_value` | `INTEGER` | `NOT NULL` | 明細金額（最小通貨単位） |
| `amount_currency` | `VARCHAR(3)` | `NOT NULL` | 通貨コード（ISO 4217） |
| `seq_number` | `INTEGER` | `NOT NULL` | 明細順序（1 始まり） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `payment`（支払記録）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `invoice_id` | `BIGINT` | `FK → invoice.id, NOT NULL` | 親精算書 ID |
| `paid_amount_value` | `INTEGER` | `NOT NULL` | 支払金額（最小通貨単位） |
| `paid_amount_currency` | `VARCHAR(3)` | `NOT NULL` | 通貨コード（ISO 4217） |
| `paid_at` | `TIMESTAMPTZ` | `NOT NULL` | 支払日時 |
| `payment_method` | `VARCHAR(30)` | `NOT NULL` | 支払方法（例: `BANK_TRANSFER`, `CREDIT_CARD`） |
| `transaction_reference` | `VARCHAR(100)` | | 取引参照番号（外部決済システムの ID） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

---

### `users`（ユーザー）

Spring Security の `UserDetailsService` が参照するユーザー認証テーブル。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `username` | `VARCHAR(50)` | `UK, NOT NULL` | ログイン名 |
| `email` | `VARCHAR(200)` | `UK, NOT NULL` | メールアドレス |
| `password` | `VARCHAR(255)` | `NOT NULL` | パスワード（BCrypt ハッシュ） |
| `enabled` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | アカウント有効フラグ |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |

#### DDL

```sql
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(200) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,  -- BCrypt ハッシュ
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

### `user_roles`（ユーザーロール）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `user_id` | `BIGINT` | `PK, FK → users.id, NOT NULL` | 親ユーザー ID |
| `role` | `VARCHAR(50)` | `PK, NOT NULL` | ロール名（`ROLE_ADMIN` / `ROLE_SALES` / `ROLE_SHIPPER` 等。正典は非機能要件の RBAC ロール定義） |

#### DDL

```sql
CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    role       VARCHAR(50) NOT NULL,  -- ROLE_ADMIN / ROLE_SALES / ROLE_SHIPPER 等
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role)
);
```

---

### `estimate`（見積）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（`GENERATED BY DEFAULT AS IDENTITY`） |
| `estimate_id` | `UUID` | `UK, NOT NULL` | 見積 ID（業務キー） |
| `origin_unlocode` | `VARCHAR(5)` | `NOT NULL` | 出発地（UN/LOCODE） |
| `destination_unlocode` | `VARCHAR(5)` | `NOT NULL` | 仕向地（UN/LOCODE） |
| `arrival_deadline` | `DATE` | `NOT NULL` | 到着期限 |
| `cargo_type` | `VARCHAR(30)` | `NOT NULL` | 貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`） |
| `weight_kg` | `NUMERIC(10,3)` | `NOT NULL` | 重量（kg） |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'CREATED'` | 見積状態（`CREATED` / `EXPIRED`） |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | 楽観的ロック（判断 8）。集約ルートのテーブルにのみ付与する |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

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
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
| `estimated_cost_value` | `INTEGER` | `NOT NULL` | 見積コスト（最小通貨単位の整数） |
| `estimated_cost_currency` | `VARCHAR(3)` | `NOT NULL` | 見積コストの通貨コード（ISO 4217） |
| `priority` | `INT` | `NOT NULL, DEFAULT 0` | ルート候補の優先順位（`rank` は SQL の予約語のため改名） |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### DDL

```sql
CREATE TABLE route_candidate (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    estimate_id     BIGINT NOT NULL REFERENCES estimate(id) ON DELETE CASCADE,
    voyage_number   VARCHAR(20) NOT NULL,
    transit_port    VARCHAR(5),
    transit_days    INT NOT NULL,
    estimated_cost_value    INTEGER NOT NULL,
    estimated_cost_currency VARCHAR(3) NOT NULL,
    priority        INT NOT NULL DEFAULT 0,  -- rank は SQL の予約語のため priority を用いる
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_route_candidate_estimate ON route_candidate (estimate_id);
```

---

## 設計上の判断

### 1. サロゲートキーと業務キーの併用

**判断**: 全テーブルに `BIGSERIAL` のサロゲートキー（`id`）を設け、業務上の識別子（`booking_id`、`voyage_number`、`unlocode` 等）には `UNIQUE` 制約を付与する。

**根拠**: 外部キー参照を `BIGINT` に統一することでインデックス効率が向上する。業務キーはドメインモデルの一部であり、別途管理することで業務ルールの変更に対応しやすい。

**例外**: `shipper.id` はアプリケーション側で採番する `UUID` を採用している（V3 マイグレーション）。荷主 ID はアプリケーションサービスが `UUID` を採番して `ShipperId` 値オブジェクトとして保持するため、DB 採番に依存しない。これに伴い `cargo.shipper_id`・`cargo.booking_id`（`BookingId` も同様に `UUID` 採番）も `UUID` である（V4 マイグレーション）。

---

### 2. `location` テーブルへの参照方式

**判断**: 参考実装では `VARCHAR` で場所 ID を文字列管理していたが、本設計では `location.unlocode` を外部キーとして参照する。

**根拠**: UN/LOCODE は国際標準の 5 文字コードであり、それ自体が意味を持つ自然キーである。文字列参照でも JOIN 効率は許容範囲内であり、可読性が高まる。

---

### 3. 金額の表現（`INTEGER` + `VARCHAR(3)`）

**判断**: 金額を `INTEGER`（最小通貨単位）と `VARCHAR(3)`（ISO 4217 通貨コード）の 2 カラムで表現する。`NUMERIC` / `DECIMAL` は使用しない。

**根拠**: 浮動小数点演算による精度誤差を排除するため、円・セントなど最小通貨単位で整数管理する。複数通貨対応のため通貨コードを常に付随させる。これはドメインモデルの `Money` 値オブジェクトに対応する。

---

### 4. 列挙値のカラム型（`VARCHAR(30)`）

**判断**: `BookingStatus`、`TransportStatus`、`HandlingType` 等の列挙型カラムは `VARCHAR(30)` で表現し、PostgreSQL の `ENUM` 型は使用しない。

**根拠**: PostgreSQL `ENUM` 型は値の追加・変更にスキーマ ALTER が必要でマイグレーション時のリスクが高い。`VARCHAR` ならば Flyway マイグレーションで CHECK 制約を追加・変更するだけで済む。

---

### 5. コンテキスト間の参照整合性

**判断**: 異なるコンテキスト間（例: `handling_activity.booking_id` → `cargo.booking_id`）には DB 外部キー制約を設けない。コンテキスト内の参照（例: `leg.cargo_id` → `cargo.id`）には外部キー制約を設ける。

**根拠**: DDD の境界付けられたコンテキスト間はイベント連携を前提とする疎結合設計であり、DB 外部キーによる強結合は将来のサービス分割を妨げる。整合性はアプリケーション層で保証する。

---

### 6. `Billing Context` の新規設計

**判断**: 参考実装（Jakarta EE）には `Billing Context` が存在しなかったが、本設計では `invoice`・`invoice_line_item`・`payment` の 3 テーブルを新規追加する。

**根拠**: ドメインモデル分析で識別した `SETTLED`（BookingStatus）と `Invoice` エンティティを実現するために必要。経理担当者のユースケース（精算書生成・支払確認）を支える永続化構造として設計した。

---

### 7. 監査カラムの全テーブル付与

**判断**: `created_at`・`updated_at` を全テーブルに `NOT NULL DEFAULT NOW()` で付与する。**例外は設けない**（旧版は `user_roles` と `route_candidate` が本方針に違反していたため是正した）。`updated_at` の更新は MyBatis マッパー側で `CURRENT_TIMESTAMP` をセットする。

**根拠**: 国際貨物輸送は規制上の監査要件が高く、全レコードの作成・更新タイムスタンプが必要。PostgreSQL のトリガーで自動更新する方法もあるが、更新経路をアプリケーション側に集約したほうが「いつ誰が更新したか」をコード上で追跡でき、テストからも制御しやすいため、マッパー側で制御する。

> 本判断の根拠は当初「H2 との互換性」だったが、更新経路をアプリケーション側に集約する理由に差し替えた。ADR-003 の改訂で H2 はローカル起動用に復活したが、この判断の根拠としては用いない（ローカルと本番で更新経路が変わってはならないため）。

---

### 8. 楽観的ロック（`version` カラム）

**判断**: 集約ルートに対応するテーブル（`cargo` / `shipper` / `voyage` / `tracking_activity` / `handling_activity` / `invoice` / `estimate`）に `version BIGINT NOT NULL DEFAULT 0` を付与し、UPDATE 時に `WHERE id = ? AND version = ?` で競合を検出する。更新が 0 行だった場合は `OptimisticLockingFailureException` を送出する。

**根拠**: 荷役登録イベント（`HandlingActivityRegisteredEvent`）は `tracking_activity` と `cargo` の両方を更新する設計であり（`architecture_backend.md`）、荷役は本システムで最も頻度の高い操作である。**最も頻度の高い操作が複数集約の同時更新を伴う以上、lost update は例外ではなく日常的に起きる。**

`test_strategy.md` は統合テストの検証対象に楽観的ロックを挙げていたが、この列が無いため**検証対象が存在せず、テストが書けない状態**だった。文言だけの安全装置は安全装置ではない。

**コンプライアンス**: 「同一の `Cargo` を 2 スレッドから更新すると後勝ちが `OptimisticLockingFailureException` になる」統合テストを DoD に含める。**安全装置は「入れたこと」ではなく「働くこと」を、実際に競合を起こすテストで固定する。**

---

### 9. インデックス設計

**判断**: 主キー・UNIQUE 制約による自動インデックスに加え、以下に明示的なインデックスを作成する。

| テーブル | インデックス対象 | 用途 |
| :--- | :--- | :--- |
| `cargo` | `shipper_id` | 荷主別の予約一覧 |
| `cargo` | `booking_status` | ダッシュボードの状態別件数・一覧の絞り込み |
| `leg` | `cargo_id` | 旅程の取得（集約ロード時に必ず引く） |
| `leg` | `voyage_number` | 航海に紐づく貨物の逆引き |
| `carrier_movement` | `voyage_id` | 航海スケジュールの取得 |
| `tracking_activity` | `booking_id` | 予約からの追跡レコード引き当て |
| `tracking_handling_event` | `tracking_id, event_time DESC` | タイムライン表示（時系列降順が既定の並び） |
| `tracking_exception_event` | `tracking_id` | 例外一覧 |
| `tracking_exception_event` | `resolved_at`（部分インデックス: `WHERE resolved_at IS NULL`） | **未解決例外の一覧**。ダッシュボードで毎朝引く最重要クエリ |
| `handling_activity` | `booking_id` | 予約別の荷役履歴 |
| `handling_activity` | `event_completion_time DESC` | 最新荷役の一覧 |
| `customs_declaration` | `handling_activity_id` | 荷役からの通関申告引き当て |
| `invoice` | `booking_id` | 予約からの請求書引き当て（UNIQUE により自動作成） |
| `invoice` | `payment_status`, `due_date` | 支払期限超過の抽出 |
| `route_candidate` | `estimate_id` | 見積のルート候補取得 |
| `booking_route_proposal` | `booking_id` | 予約からの経路提案引き当て（UNIQUE により自動作成） |
| `proposed_route` | `proposal_id, priority` | 候補一覧の取得（表示順で引く） |

**根拠**: `non_functional.md` は公開追跡 API に p95 200ms を要求しているが、旧版のデータモデルには `CREATE INDEX` が 1 件も無く、**性能目標が物理設計として裏づけられていなかった**。FK 相当の列と一覧画面の絞り込み条件には索引が要る。

**部分インデックス（`WHERE resolved_at IS NULL`）は PostgreSQL 固有の機能であり、`db/migration/postgresql/` に隔離する**（ADR-003）。H2 は部分インデックスを解釈できないため、`common/` に置くとローカル起動が失敗する。

その結果、**ローカル（H2）ではこのインデックスが存在しない**。ローカルで「未解決例外の一覧」が速いことは、本番で速いことを意味しない。**インデックスの効果は Repository テスト（実 PostgreSQL）と負荷試験で確認する。**

**コンプライアンス**: 追跡 API に対する負荷試験を Release 1 で 1 本実施し（`docs/development/release_scope.md`）、実行計画が Index Scan になっていることを確認する。

---

## Flyway マイグレーション方針

### ファイル命名規則

```text
src/main/resources/db/migration/
├── common/                    # H2 と PostgreSQL の両方で実行される
│   ├── V1__init.sql           # 初期スキーマ全テーブル作成
│   ├── V2__seed_locations.sql # 初期 UN/LOCODE マスタデータ
│   └── V3__add_xxx.sql        # 機能追加に伴うスキーマ変更
├── postgresql/                # PostgreSQL でのみ実行
│   └── V101__partial_indexes.sql
└── h2/                        # H2 でのみ実行（原則として空）
```

バージョン番号は `common/` と `postgresql/` で重複させない（`postgresql/` は 101 番台から始める）。Flyway は両方のロケーションを 1 つの系列として扱うため、重複するとチェックサム検証で失敗する。

### マイグレーションルール

- バージョン番号は連番とし、番号の欠番を作らない
- 既存マイグレーションファイルの編集は禁止（Flyway チェックサム検証）
- ロールバックは Undo マイグレーション（`U` プレフィックス）ではなく、**Forward マイグレーション + Expand-Contract パターン**で対応する（Undo は Flyway Community Edition では実行できない。`operation.md` の記述が正典）
- **`db/migration/common/` に置く DDL は H2 と PostgreSQL の両方で動く構文に限る**（ADR-003）。ローカル起動に H2 を使うためである
- **PostgreSQL 固有の構文は `db/migration/postgresql/` に隔離する**。部分インデックスが該当する
- `db/migration/h2/` は原則として空にする。**ここにテーブル定義が増え始めたら、共通部分が分岐している兆候**であり設計を見直す合図とする
- 分離の代償として、**開発中に見ているスキーマと本番のスキーマは完全には一致しない**。この差分を許容する代わりに、SQL の検証は実 PostgreSQL で行う

### `V1__init.sql` の構成イメージ

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
CREATE TABLE booking_route_proposal ( ... );  -- 予約に紐づく経路候補の置き場（US09 / US10）
CREATE TABLE proposed_route ( ... );

-- Tracking Context
CREATE TABLE tracking_activity ( ... );
CREATE TABLE tracking_handling_event ( ... );
CREATE TABLE tracking_exception_event ( ... );  -- escalation_flag / status_before / resolution_notes あり

-- Tracking Context / Handling モジュール
CREATE TABLE handling_activity ( ... );
CREATE TABLE customs_declaration ( ... );

-- Billing Context
CREATE TABLE invoice ( ... );  -- tax_rate / tax_amount / booking_id UNIQUE あり
CREATE TABLE invoice_line_item ( ... );
CREATE TABLE payment ( ... );

-- Estimation Context (V8__add_estimate.sql)
CREATE TABLE estimate ( ... );       -- estimate_id UUID UNIQUE あり
CREATE TABLE route_candidate ( ... ); -- estimate FK (CASCADE 削除) あり
```
