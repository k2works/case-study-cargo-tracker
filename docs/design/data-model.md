---
title: データモデル設計
description: 国際貨物輸送管理システムのデータモデル設計。マイクロサービスの Database per Service パターンに基づく概念データモデル・論理データモデル・テーブル定義を含む。
published: true
date: 2026-08-19T00:00:00.000Z
tags: design,data-model,microservices
---

# データモデル設計 - 国際貨物輸送管理システム

## 概要

本ドキュメントは、国際貨物輸送管理システムの永続化層データモデルを定義する。
バックエンドアーキテクチャで定義した 7 つの境界付けられたコンテキスト（Auth / Booking / Routing / Tracking / Handling / Billing / Shared Domain）に対応する **6 つの独立データベース** と **22 テーブル** を設計する。
マイクロサービスアーキテクチャの **Database per Service** パターンに従い、各サービスが専用のデータベースを持つ。

take-3 のデータモデルを基礎とし、本プロジェクトの要件差分として
アカウント保護カラム・認証監査ログ（US31）・キャンセル申請（UC22）・通関申告の独立集約化と状態変更履歴（UC21）・キャンセル料（UC22）を反映している。

### 設計方針

- **アーキテクチャ**: Database per Service（マイクロサービスパターン）
- **DB**: PostgreSQL 16.x（本番）、H2（開発環境 Heroku・テスト）
- **ORM**: MyBatis（XML マッパー）
- **マイグレーション**: Flyway（`V1__init.sql` 形式）— サービスごとに独立管理
- **ID 戦略**: サロゲートキー（`BIGSERIAL`）+ 業務キー（`VARCHAR`）の併用
- **命名規則**: スネークケース（PostgreSQL 慣習）
- **監査カラム**: 全テーブルに `created_at` / `updated_at` を付与
- **コンテキスト間整合性**: DB 外部キー制約ではなくイベント連携で保証

### データベース配置

| サービス | データベース名 | 管理テーブル |
| :--- | :--- | :--- |
| authms | `auth_db` | `users`, `user_roles`, `auth_audit_log` |
| bookingms | `booking_db` | `location`, `shipper`, `cargo`, `leg`, `estimate`, `route_candidate`, `cancellation_request` |
| routingms | `routing_db` | `location`, `voyage`, `carrier_movement` |
| trackingms | `tracking_db` | `location`, `tracking_activity`, `tracking_handling_event`, `tracking_exception_event` |
| handlingms | `handling_db` | `location`, `handling_activity`, `customs_declaration`, `customs_status_history` |
| billingms | `billing_db` | `invoice`, `invoice_line_item`, `payment` |

> **`location` テーブルの重複について**: Shared Domain の `Location`（UN/LOCODE）は共有カーネルとして定義されるが、Database per Service パターンでは各サービスが自身の DB 内に `location` テーブルを保持する。初期データは共通の Flyway シードスクリプトから投入し、データの同期は必要に応じてイベントで行う。
>
> **形（[ADR-010](../adr/010-location-master-shape.md)）**: 主キーはサロゲート（`id BIGSERIAL`）、`unlocode` に UNIQUE 制約を置く。参照側（`cargo` 等）は `unlocode` で持つ。`time_zone` は **NOT NULL**（到着期限を目的地の暦で判断するために必要で、後から必須にすると既存行が読めなくなる）。マスタの正は bookingms が持ち、他サービスへの複製の同期方法は [ADR-014](../adr/014-location-replica-sync.md) で決めた。同一内容の種データマイグレーションを地点を使う全サービスへ配り、ずれは `LocationSeedReplicaTest`（shared）が落とす。実行時のイベント同期は行わない。

---

## 概念データモデル

全コンテキストのエンティティとその主要リレーションシップを俯瞰する。マイクロサービス境界（データベース境界）を明示し、コンテキスト間の参照は業務キー（`booking_id`、`voyage_number` 等）による論理参照とする。

```plantuml
@startuml
title 概念データモデル - 国際貨物輸送管理システム（マイクロサービス）

skinparam entity {
  BackgroundColor White
  BorderColor Black
}

package "auth_db\n(Auth Context)" #LightSkyBlue {
  entity "users\n（ユーザー）" as users {
    * id : BIGINT <<PK>>
    --
    * username : VARCHAR(50) <<UK>>
    * email : VARCHAR(200) <<UK>>
    * password : VARCHAR(255)
    * enabled : BOOLEAN
    * failed_attempts : INTEGER
    locked_until : TIMESTAMP
  }

  entity "user_roles\n（ユーザーロール）" as user_roles {
    * user_id : BIGINT <<FK, PK>>
    * role : VARCHAR(50) <<PK>>
  }

  entity "auth_audit_log\n（認証監査ログ）" as auth_audit_log {
    * id : BIGINT <<PK>>
    --
    * username : VARCHAR(50)
    * event_type : VARCHAR(30)
    * occurred_at : TIMESTAMP
  }
}

package "booking_db\n(Booking Context)" #LightBlue {
  entity "location\n（場所）" as b_location {
    * id : BIGINT <<PK>>
    --
    * unlocode : VARCHAR(5) <<UK>>
    * name : VARCHAR(100)
  }

  entity "shipper\n（荷主）" as shipper {
    * id : BIGINT <<PK>>
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
    * shipper_id : BIGINT <<FK>>
    * booking_status : VARCHAR(30)
    * transport_status : VARCHAR(30)
    * routing_status : VARCHAR(30)
    * cargo_type : VARCHAR(20)
    * weight_kg : NUMERIC(10,3)
  }

  entity "leg\n（輸送区間）" as leg {
    * id : BIGINT <<PK>>
    --
    * cargo_id : BIGINT <<FK>>
    * voyage_number : VARCHAR(20)
    * load_location_unlocode : VARCHAR(5) <<FK>>
    * unload_location_unlocode : VARCHAR(5) <<FK>>
  }

  entity "estimate\n（見積）" as estimate {
    * id : BIGINT <<PK>>
    --
    * estimate_id : UUID <<UK>>
    * origin_unlocode : VARCHAR(5)
    * destination_unlocode : VARCHAR(5)
    * status : VARCHAR(20)
  }

  entity "route_candidate\n（ルート候補）" as route_candidate {
    * id : BIGINT <<PK>>
    --
    * estimate_id : BIGINT <<FK>>
    * voyage_number : VARCHAR(20)
    * rank : INT
  }

  entity "cancellation_request\n（キャンセル申請）" as cancellation_request {
    * id : BIGINT <<PK>>
    --
    * cargo_id : BIGINT <<FK>>
    * reason : VARCHAR(500)
    * status : VARCHAR(20)
    * requested_by : VARCHAR(100)
    * requested_at : TIMESTAMP
    discharge_location_unlocode : VARCHAR(5)
    decided_by : VARCHAR(100)
    decided_at : TIMESTAMP
    decision_reason : VARCHAR(500)
  }
}

package "routing_db\n(Routing Context)" #LightGreen {
  entity "location\n（場所）" as r_location {
    * id : BIGINT <<PK>>
    --
    * unlocode : VARCHAR(5) <<UK>>
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
  }
}

package "tracking_db\n(Tracking Context)" #LightYellow {
  entity "location\n（場所）" as t_location {
    * id : BIGINT <<PK>>
    --
    * unlocode : VARCHAR(5) <<UK>>
  }

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
  }

  entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
    * id : BIGINT <<PK>>
    --
    * tracking_id : BIGINT <<FK>>
    * exception_type : VARCHAR(50)
    * occurred_at : TIMESTAMP
    * escalation_flag : BOOLEAN
    resolved_at : TIMESTAMP
  }
}

package "handling_db\n(Handling Context)" #LightCoral {
  entity "location\n（場所）" as h_location {
    * id : BIGINT <<PK>>
    --
    * unlocode : VARCHAR(5) <<UK>>
  }

  entity "handling_activity\n（荷役作業記録）" as handling_activity {
    * id : BIGINT <<PK>>
    --
    * booking_id : VARCHAR(20)
    * event_type : VARCHAR(30)
    * event_completion_time : TIMESTAMP
    * location_unlocode : VARCHAR(5) <<FK>>
  }

  entity "customs_declaration\n（通関申告）" as customs_declaration {
    * id : BIGINT <<PK>>
    --
    * declaration_number : VARCHAR(50) <<UK>>
    * booking_id : VARCHAR(20)
    * tracking_number : VARCHAR(20)
    * declared_at : TIMESTAMP
    * status : VARCHAR(30)
    cleared_at : TIMESTAMP
  }

  entity "customs_status_history\n（通関状態履歴）" as customs_status_history {
    * id : BIGINT <<PK>>
    --
    * customs_declaration_id : BIGINT <<FK>>
    * from_status : VARCHAR(30)
    * to_status : VARCHAR(30)
    * changed_by : VARCHAR(100)
    * changed_at : TIMESTAMP
    * reason : VARCHAR(500)
  }
}

package "billing_db\n(Billing Context)" #LightPink {
  entity "invoice\n（精算書）" as invoice {
    * id : BIGINT <<PK>>
    --
    * invoice_number : VARCHAR(30) <<UK>>
    * booking_id : VARCHAR(20) <<UK>>
    * payment_status : VARCHAR(30)
    cancellation_fee_value : INTEGER
  }

  entity "invoice_line_item\n（精算明細）" as invoice_line_item {
    * id : BIGINT <<PK>>
    --
    * invoice_id : BIGINT <<FK>>
    * description : VARCHAR(200)
  }

  entity "payment\n（支払記録）" as payment {
    * id : BIGINT <<PK>>
    --
    * invoice_id : BIGINT <<FK>>
    * paid_at : TIMESTAMP
  }
}

' ===== DB 内リレーション（実線） =====

' auth_db
users ||--o{ user_roles : "ロールを持つ"

' booking_db
cargo }o--|| shipper : "荷主"
cargo ||--o{ leg : "旅程を持つ"
cargo ||--o{ cancellation_request : "キャンセル申請を持つ"
leg }o--|| b_location : "積込場所"
leg }o--|| b_location : "荷降場所"
estimate ||--o{ route_candidate : "ルート候補を持つ"

' routing_db
voyage ||--o{ carrier_movement : "運送区間を持つ"
carrier_movement }o--|| r_location : "出発地"
carrier_movement }o--|| r_location : "到着地"

' tracking_db
tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"
tracking_handling_event }o--o| t_location : "発生場所"

' handling_db
customs_declaration ||--o{ customs_status_history : "状態履歴を持つ"
handling_activity }o--|| h_location : "作業場所"

' billing_db
invoice ||--o{ invoice_line_item : "明細を持つ"
invoice ||--o{ payment : "支払を持つ"

' ===== コンテキスト間の論理参照（点線）=====
cargo .right.> voyage : "voyage_number\n（論理参照）"
tracking_activity ..> cargo : "booking_id\n（論理参照）"
handling_activity ..> cargo : "booking_id\n（論理参照）"
customs_declaration ..> cargo : "booking_id\n（論理参照）"
invoice ..> cargo : "booking_id\n（論理参照）"

@enduml
```

---

## 論理データモデル

> **適用済みのマイグレーションは編集しない。** 内容を変えるとチェックサムが変わり、
> 既に適用した環境の起動が止まる（IT1 で V3 を書き換えて kind の authms が
> `Migration checksum mismatch` で落ちた）。既存データに手を入れたいときは、
> 新しい番号のマイグレーションを足す。

### auth_db — Auth Context

ユーザー認証・認可テーブル。JWT トークン発行・検証のために `authms` が専有する。
アカウント保護（US31）のため `failed_attempts` / `locked_until` を保持し、認証試行・ロック・解除は `auth_audit_log` に記録する。

```plantuml
@startuml
title 論理データモデル - auth_db（Auth Context）

entity "users\n（ユーザー）" as users {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * username : VARCHAR(50) <<UK, NOT NULL>>
  * email : VARCHAR(200) <<UK, NOT NULL>>
  * password : VARCHAR(255) <<NOT NULL>>
  * enabled : BOOLEAN <<NOT NULL, DEFAULT TRUE>>
  * failed_attempts : INTEGER <<NOT NULL, DEFAULT 0>>
  locked_until : TIMESTAMP WITH TIME ZONE
  * created_at : TIMESTAMP WITH TIME ZONE <<NOT NULL, DEFAULT NOW()>>
}

entity "user_roles\n（ユーザーロール）" as user_roles {
  * user_id : BIGINT <<FK, PK>>
  * role : VARCHAR(50) <<PK>>
}

entity "auth_audit_log\n（認証監査ログ）" as auth_audit_log {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * username : VARCHAR(50) <<NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * occurred_at : TIMESTAMP WITH TIME ZONE <<NOT NULL, DEFAULT NOW()>>
  detail : VARCHAR(500)
}

users ||--o{ user_roles : "ロールを持つ"

@enduml
```

---

### booking_db — Booking Context

貨物の予約・旅程・見積・キャンセル申請を管理する。`cargo` が集約ルートで、`leg` が旅程の各区間、`cancellation_request` がキャンセル承認フロー（UC22）を表す。荷主情報は `shipper` テーブルに正規化し、FK 参照とする。

> **状態列と料金列（[ADR-009](../adr/009-cargo-status-columns-from-the-start.md)）**: `transport_status` / `routing_status` は「まだ動いていない」という意味のある状態（`NOT_RECEIVED` / `NOT_ROUTED`）を持つため、最初から **NOT NULL** とする。一方 `booking_amount_*` は計算結果であり、料金を算出する US18（IT11）まで値が無い。**NULL を許し、0 で埋めない**（0 円と未算出が区別できなくなり、算出漏れが無料の予約として通る）。後から NOT NULL にもしない（見積の無い期間に入った行が読めなくなる）。

```plantuml
@startuml
title 論理データモデル - booking_db（Booking Context）

entity "location\n（場所）" as location {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * unlocode : VARCHAR(5) <<UK, NOT NULL>>
  * name : VARCHAR(100) <<NOT NULL>>
  country_code : VARCHAR(2)
  * time_zone : VARCHAR(50) <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL>>
  * updated_at : TIMESTAMPTZ <<NOT NULL>>
}

entity "shipper\n（荷主）" as shipper {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * shipper_code : VARCHAR(20) <<UK, NOT NULL>>
  * shipper_type : VARCHAR(20) <<NOT NULL>>
  * name : VARCHAR(200) <<NOT NULL>>
  * email : VARCHAR(200) <<NOT NULL>>
  * address : VARCHAR(500) <<NOT NULL>>
  phone : VARCHAR(50)
  contract_number : VARCHAR(50)
  discount_rate : NUMERIC(5,4)
  * created_at : TIMESTAMPTZ <<NOT NULL>>
  * updated_at : TIMESTAMPTZ <<NOT NULL>>
}

entity "cargo\n（貨物）" as cargo {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : VARCHAR(20) <<UK, NOT NULL>>
  * shipper_id : BIGINT <<FK, NOT NULL>>
  * booking_status : VARCHAR(30) <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
  * routing_status : VARCHAR(30) <<NOT NULL>>
  * cargo_type : VARCHAR(20) <<NOT NULL, DEFAULT 'GENERAL'>>
  * weight_kg : NUMERIC(10,3) <<NOT NULL>>
  declared_value : NUMERIC(15,2)
  * spec_origin_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * spec_destination_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * spec_arrival_deadline : DATE <<NOT NULL>>
  spec_departure_date : DATE
  origin_unlocode : VARCHAR(5) <<FK>>
  booking_amount_value : INTEGER
  booking_amount_currency : VARCHAR(3)
  consignee_name : VARCHAR(200)
  consignee_email : VARCHAR(200)
  tracking_number : VARCHAR(20)
  last_known_location_unlocode : VARCHAR(5)
  current_voyage_number : VARCHAR(20)
  last_handling_event_type : VARCHAR(30)
  last_handling_event_location : VARCHAR(5)
  last_handling_event_voyage : VARCHAR(20)
  quantity : INTEGER
  description : VARCHAR(500)
  length : NUMERIC(8,2)
  width : NUMERIC(8,2)
  height : NUMERIC(8,2)
  hazardous_class : VARCHAR(20)
  un_number : VARCHAR(10)
  proper_shipping_name : VARCHAR(200)
  temp_min : NUMERIC(5,2)
  temp_max : NUMERIC(5,2)
  temp_unit : VARCHAR(10)
  * created_at : TIMESTAMPTZ <<NOT NULL>>
  * updated_at : TIMESTAMPTZ <<NOT NULL>>
}

entity "leg\n（輸送区間）" as leg {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * cargo_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<NOT NULL>>
  * load_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * unload_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  load_time : TIMESTAMP
  unload_time : TIMESTAMP
  * seq_number : INTEGER <<NOT NULL>>
}

entity "estimate\n（見積）" as estimate {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * estimate_id : UUID <<UK, NOT NULL>>
  * origin_unlocode : VARCHAR(5) <<NOT NULL>>
  * destination_unlocode : VARCHAR(5) <<NOT NULL>>
  * arrival_deadline : DATE <<NOT NULL>>
  * cargo_type : VARCHAR(30) <<NOT NULL>>
  * weight_kg : NUMERIC(10,3) <<NOT NULL>>
  * status : VARCHAR(20) <<NOT NULL, DEFAULT 'CREATED'>>
}

entity "route_candidate\n（ルート候補）" as route_candidate {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * estimate_id : BIGINT <<FK, NOT NULL>>
  * voyage_number : VARCHAR(20) <<NOT NULL>>
  transit_port : VARCHAR(5)
  * transit_days : INT <<NOT NULL>>
  * estimated_cost : NUMERIC(12,2) <<NOT NULL>>
  * rank : INT <<NOT NULL, DEFAULT 0>>
}

entity "cancellation_request\n（キャンセル申請）" as cancellation_request {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * cargo_id : BIGINT <<FK, NOT NULL>>
  * reason : VARCHAR(500) <<NOT NULL>>
  * status : VARCHAR(20) <<NOT NULL, DEFAULT 'REQUESTED'>>
  * requested_by : VARCHAR(100) <<NOT NULL>>
  * requested_at : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * booking_status_at_request : VARCHAR(30) <<NOT NULL>>
  discharge_location_unlocode : VARCHAR(5) <<FK>>
  decided_by : VARCHAR(100)
  decided_at : TIMESTAMP WITH TIME ZONE
  decision_reason : VARCHAR(500)
}

cargo }o--|| shipper : "荷主"
cargo ||--o{ leg : "旅程を持つ"
cargo ||--o{ cancellation_request : "キャンセル申請を持つ"
estimate ||--o{ route_candidate : "ルート候補を持つ"

@enduml
```

---

### routing_db — Routing Context

航海スケジュールと運送区間を管理する。`voyage` が集約ルートで、`carrier_movement` が個々の移動区間を表す。（take-3 と同一構成）

```plantuml
@startuml
title 論理データモデル - routing_db（Routing Context）

entity "location\n（場所）" as location {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * unlocode : VARCHAR(5) <<UK, NOT NULL>>
  * name : VARCHAR(100) <<NOT NULL>>
  * country_code : VARCHAR(2) <<NOT NULL>>
  * time_zone : VARCHAR(50) <<NOT NULL>>
  * created_at : TIMESTAMPTZ <<NOT NULL>>
  * updated_at : TIMESTAMPTZ <<NOT NULL>>
}

entity "voyage\n（航海）" as voyage {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * voyage_number : VARCHAR(20) <<UK, NOT NULL>>
}

entity "carrier_movement\n（運送区間）" as carrier_movement {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * voyage_id : BIGINT <<FK, NOT NULL>>
  * departure_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * arrival_location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  * departure_date : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * arrival_date : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
}

voyage ||--o{ carrier_movement : "運送区間を持つ"
carrier_movement }o--|| location : "出発地"
carrier_movement }o--|| location : "到着地"

@enduml
```

---

### tracking_db — Tracking Context

貨物追跡の状態・イベント・例外を管理する。`tracking_activity` が集約ルート。Booking Context / Handling Context からのイベントをサブスクライブしてデータを構築する CQRS 読み取り側モデル。
例外種別に `MISROUTE`（誤配、US28）と `CUSTOMS_HOLD`（税関保留、UC21）を含む。

```plantuml
@startuml
title 論理データモデル - tracking_db（Tracking Context）

entity "location\n（場所）" as location {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * unlocode : VARCHAR(5) <<UK, NOT NULL>>
  * name : VARCHAR(100) <<NOT NULL>>
}

entity "tracking_activity\n（追跡レコード）" as tracking_activity {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_number : VARCHAR(20) <<UK, NOT NULL>>
  * booking_id : VARCHAR(20) <<NOT NULL>>
  * transport_status : VARCHAR(30) <<NOT NULL>>
}

entity "tracking_handling_event\n（追跡イベント）" as tracking_handling_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_time : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK>>
  voyage_number : VARCHAR(20)
}

entity "tracking_exception_event\n（追跡例外イベント）" as tracking_exception_event {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * tracking_id : BIGINT <<FK, NOT NULL>>
  * exception_type : VARCHAR(50) <<NOT NULL>>
  * occurred_at : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * escalation_flag : BOOLEAN <<NOT NULL, DEFAULT FALSE>>
  description : VARCHAR(500)
  detected_location_unlocode : VARCHAR(5)
  resolved_at : TIMESTAMP WITH TIME ZONE
  resolution_notes : TEXT
}

tracking_activity ||--o{ tracking_handling_event : "イベントを持つ"
tracking_activity ||--o{ tracking_exception_event : "例外を持つ"
tracking_handling_event }o--o| location : "発生場所"

@enduml
```

---

### handling_db — Handling Context

荷役作業の実績と通関申告を管理する。`handling_activity` と `customs_declaration` の 2 つの集約ルートを持つ（ドメインモデル設計の集約昇格判断に対応）。
通関申告は `booking_id` / `tracking_number` で貨物を論理参照し、状態変更は必ず `customs_status_history` に記録する（UC21）。

```plantuml
@startuml
title 論理データモデル - handling_db（Handling Context）

entity "location\n（場所）" as location {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * unlocode : VARCHAR(5) <<UK, NOT NULL>>
  * name : VARCHAR(100) <<NOT NULL>>
}

entity "handling_activity\n（荷役作業記録）" as handling_activity {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * booking_id : VARCHAR(20) <<NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_completion_time : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK, NOT NULL>>
  voyage_number : VARCHAR(20)
  operator_name : VARCHAR(200)
  consignee_confirmation : VARCHAR(200)
}

entity "customs_declaration\n（通関申告）" as customs_declaration {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * declaration_number : VARCHAR(50) <<UK, NOT NULL>>
  * booking_id : VARCHAR(20) <<NOT NULL>>
  * tracking_number : VARCHAR(20) <<NOT NULL>>
  * declared_at : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * status : VARCHAR(30) <<NOT NULL, DEFAULT 'PENDING'>>
  cleared_at : TIMESTAMP WITH TIME ZONE
  remarks : VARCHAR(500)
}

entity "customs_status_history\n（通関状態履歴）" as customs_status_history {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * customs_declaration_id : BIGINT <<FK, NOT NULL>>
  * from_status : VARCHAR(30) <<NOT NULL>>
  * to_status : VARCHAR(30) <<NOT NULL>>
  * changed_by : VARCHAR(100) <<NOT NULL>>
  * changed_at : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * reason : VARCHAR(500) <<NOT NULL>>
}

customs_declaration ||--o{ customs_status_history : "状態履歴を持つ"
handling_activity }o--|| location : "作業場所"

@enduml
```

---

### billing_db — Billing Context

精算書・明細・支払記録を管理する。Tracking Context からの `CargoDeliveredEvent`、Booking Context からの `CargoCancelledEvent` をサブスクライブして精算書を自動生成する。キャンセル料は算定根拠（キャンセル時の予約状態・料率）とともに保持する（UC22）。

```plantuml
@startuml
title 論理データモデル - billing_db（Billing Context）

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
  issued_at : TIMESTAMP WITH TIME ZONE
  due_date : DATE
  discount_amount_value : INTEGER
  discount_amount_currency : VARCHAR(3)
  cancellation_fee_value : INTEGER
  cancellation_fee_currency : VARCHAR(3)
  cancellation_fee_rate : NUMERIC(5,4)
  booking_status_at_cancel : VARCHAR(30)
}

entity "invoice_line_item\n（精算明細）" as invoice_line_item {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * description : VARCHAR(200) <<NOT NULL>>
  * amount_value : INTEGER <<NOT NULL>>
  * amount_currency : VARCHAR(3) <<NOT NULL>>
  * seq_number : INTEGER <<NOT NULL>>
}

entity "payment\n（支払記録）" as payment {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * invoice_id : BIGINT <<FK, NOT NULL>>
  * paid_amount_value : INTEGER <<NOT NULL>>
  * paid_amount_currency : VARCHAR(3) <<NOT NULL>>
  * paid_at : TIMESTAMP WITH TIME ZONE <<NOT NULL>>
  * payment_method : VARCHAR(30) <<NOT NULL>>
  transaction_reference : VARCHAR(100)
}

invoice ||--o{ invoice_line_item : "明細を持つ"
invoice ||--o{ payment : "支払を持つ"

@enduml
```

---

## テーブル定義

> take-3 と共通のテーブル（`location`・`shipper`・`cargo`・`leg`・`estimate`・`route_candidate`・`voyage`・`carrier_movement`・`tracking_*`・`invoice_line_item`・`payment`）のカラム仕様は take-3 版を踏襲する。以下は take-7 で追加・変更したテーブルを中心に定義する。

### auth_db

#### `users`（ユーザー）― 変更

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `username` | `VARCHAR(50)` | `UK, NOT NULL` | ログイン名 |
| `email` | `VARCHAR(200)` | `UK, NOT NULL` | メールアドレス |
| `display_name` | `VARCHAR(100)` | `NOT NULL` | 画面に表示する呼び名（IT1 で追加。利用者 ID やメールアドレスで代用すると誰として入っているかが読みにくい） |
| `password` | `VARCHAR(255)` | `NOT NULL` | パスワード（BCrypt ハッシュ） |
| `enabled` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | アカウント有効フラグ |
| `failed_attempts` | `INTEGER` | `NOT NULL, DEFAULT 0` | 連続認証失敗回数（成功時に 0 リセット、US31） |
| `locked_until` | `TIMESTAMP WITH TIME ZONE` | | ロック期限（NULL = 未ロック。5 回失敗で設定） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |

##### DDL

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(200) NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NOT NULL,  -- BCrypt ハッシュ
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

#### `user_roles`（ユーザーロール）

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `user_id` | `BIGINT` | `PK, FK → users.id, NOT NULL` | 親ユーザー ID |
| `role` | `VARCHAR(50)` | `PK, NOT NULL` | ロール名（`ROLE_SHIPPER` / `ROLE_SALES` / `ROLE_ROUTING` / `ROLE_HANDLER` / `ROLE_TRACKER` / `ROLE_ACCOUNTANT` / `ROLE_ADMIN`。IT1 でロール名を 7 値に確定。ui_design.md と同一） |

#### `auth_audit_log`（認証監査ログ）― 追加

認証試行・ロック・解除・無効化アカウントのログイン試行を記録する（US31）。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `username` | `VARCHAR(50)` | `NOT NULL` | 対象ユーザー名（未登録名の試行も記録するため FK は張らない） |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOCKED` / `UNLOCKED` / `DISABLED_ATTEMPT` / `LOGOUT` |
| `occurred_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | 発生日時 |
| `detail` | `VARCHAR(500)` | | 補足情報（接続元等） |

##### DDL

```sql
CREATE TABLE auth_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    event_type  VARCHAR(30)  NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    detail      VARCHAR(500)
);
CREATE INDEX idx_auth_audit_log_username ON auth_audit_log (username, occurred_at);
```

---

### booking_db

#### `cancellation_request`（キャンセル申請）― 追加

キャンセル承認フロー（UC22）の申請・承認・却下の記録。理由は申請・却下ともに必須。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `cargo_id` | `BIGINT` | `FK → cargo.id, NOT NULL` | 対象貨物 ID |
| `reason` | `VARCHAR(500)` | `NOT NULL` | キャンセル理由（申請時必須） |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'REQUESTED'` | `REQUESTED` / `APPROVED` / `REJECTED` |
| `requested_by` | `VARCHAR(100)` | `NOT NULL` | 申請者（営業担当者） |
| `requested_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 申請日時 |
| `booking_status_at_request` | `VARCHAR(30)` | `NOT NULL` | 申請時の予約状態（キャンセル料算定の根拠） |
| `discharge_location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode` | 陸揚げ地（輸送中キャンセル承認時に指定） |
| `decided_by` | `VARCHAR(100)` | | 承認・却下の実行者（追跡管理者） |
| `decided_at` | `TIMESTAMP WITH TIME ZONE` | | 承認・却下日時 |
| `decision_reason` | `VARCHAR(500)` | | 承認・却下理由（却下時必須はアプリ層で検証） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

##### DDL

```sql
CREATE TABLE cancellation_request (
    id                          BIGSERIAL PRIMARY KEY,
    cargo_id                    BIGINT       NOT NULL REFERENCES cargo(id),
    reason                      VARCHAR(500) NOT NULL,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    requested_by                VARCHAR(100) NOT NULL,
    requested_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_status_at_request   VARCHAR(30)  NOT NULL,
    discharge_location_unlocode VARCHAR(5),
    decided_by                  VARCHAR(100),
    decided_at                  TIMESTAMP WITH TIME ZONE,
    decision_reason             VARCHAR(500),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cancellation_request_status ON cancellation_request (status);
```

> 承認待ち一覧（追跡管理者）は `status = 'REQUESTED'` のクエリで提供する（CQRS のクエリ側）。

---

### handling_db

#### `handling_activity`（荷役作業記録）― 変更

`event_type` は `RECEIVE` / `LOAD` / `UNLOAD` / `CLAIM` の 4 種（take-3 の `CUSTOMS` は通関申告の集約独立に伴い廃止）。引取（CLAIM）時は荷受人確認を記録する。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `booking_id` | `VARCHAR(20)` | `NOT NULL` | 予約 ID（`booking_db` への論理参照） |
| `event_type` | `VARCHAR(30)` | `NOT NULL` | 荷役タイプ（`RECEIVE` / `LOAD` / `UNLOAD` / `CLAIM`） |
| `event_completion_time` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 荷役完了日時 |
| `location_unlocode` | `VARCHAR(5)` | `FK → location.unlocode, NOT NULL` | 作業場所（UN/LOCODE） |
| `voyage_number` | `VARCHAR(20)` | | 関連する航海番号（LOAD / UNLOAD 時に必須） |
| `operator_name` | `VARCHAR(200)` | | 作業員名 |
| `consignee_confirmation` | `VARCHAR(200)` | | 荷受人確認（署名または確認コード。CLAIM 時に設定） |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

#### `customs_declaration`（通関申告）― 変更

take-3 の `handling_activity_id` FK を廃止し、独立した集約ルートとして `booking_id` / `tracking_number` の論理参照に変更した（ドメインモデル設計の集約昇格判断に対応）。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `declaration_number` | `VARCHAR(50)` | `UK, NOT NULL` | 申告番号（業務キー） |
| `booking_id` | `VARCHAR(20)` | `NOT NULL` | 予約 ID（論理参照。検索キー） |
| `tracking_number` | `VARCHAR(20)` | `NOT NULL` | 追跡番号（論理参照。申告登録時の入力キー） |
| `declared_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 申告日時 |
| `status` | `VARCHAR(30)` | `NOT NULL, DEFAULT 'PENDING'` | 通関状態（`PENDING` / `CLEARED` / `HELD` / `REJECTED`） |
| `cleared_at` | `TIMESTAMP WITH TIME ZONE` | | 通関完了日時（NULL = 未完了） |
| `remarks` | `VARCHAR(500)` | | 備考・メモ |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL, DEFAULT NOW()` | レコード更新日時 |

##### DDL

```sql
CREATE TABLE customs_declaration (
    id                 BIGSERIAL PRIMARY KEY,
    declaration_number VARCHAR(50) NOT NULL UNIQUE,
    booking_id         VARCHAR(20) NOT NULL,
    tracking_number    VARCHAR(20) NOT NULL,
    declared_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    cleared_at         TIMESTAMP WITH TIME ZONE,
    remarks            VARCHAR(500),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customs_declaration_booking ON customs_declaration (booking_id);
CREATE INDEX idx_customs_declaration_status ON customs_declaration (status, declared_at);
```

> 「HELD のまま 3 日超」の督促対象は `status = 'HELD' AND` 最終 HELD 遷移日時（`customs_status_history` の最新 `changed_at`）で判定する。CLAIM 荷役の通関ガードは `booking_id` で最新の申告を参照して `status = 'CLEARED'` を検証する。

#### `customs_status_history`（通関状態履歴）― 追加

通関状態変更の監査履歴（UC21）。状態更新には理由が必須。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PK, NOT NULL` | サロゲートキー（BIGSERIAL） |
| `customs_declaration_id` | `BIGINT` | `FK → customs_declaration.id, NOT NULL` | 親申告 ID |
| `from_status` | `VARCHAR(30)` | `NOT NULL` | 変更前状態 |
| `to_status` | `VARCHAR(30)` | `NOT NULL` | 変更後状態 |
| `changed_by` | `VARCHAR(100)` | `NOT NULL` | 変更者（追跡管理者） |
| `changed_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` | 変更日時 |
| `reason` | `VARCHAR(500)` | `NOT NULL` | 変更理由（必須） |

##### DDL

```sql
CREATE TABLE customs_status_history (
    id                     BIGSERIAL PRIMARY KEY,
    customs_declaration_id BIGINT       NOT NULL REFERENCES customs_declaration(id),
    from_status            VARCHAR(30)  NOT NULL,
    to_status              VARCHAR(30)  NOT NULL,
    changed_by             VARCHAR(100) NOT NULL,
    changed_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    reason                 VARCHAR(500) NOT NULL
);
```

---

### billing_db

#### `invoice`（精算書）― 変更

キャンセル料の算定根拠カラムを追加（UC22）。

| 追加カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `cancellation_fee_value` | `INTEGER` | | キャンセル料（最小通貨単位。NULL = キャンセルなし） |
| `cancellation_fee_currency` | `VARCHAR(3)` | | キャンセル料通貨コード |
| `cancellation_fee_rate` | `NUMERIC(5,4)` | | 適用した料率 |
| `booking_status_at_cancel` | `VARCHAR(30)` | | キャンセル時の予約状態（料率の根拠） |

その他のカラム（`invoice_number`・`booking_id`（UK・二重請求防止）・`total_amount_*`・`tax_*`・`payment_status`・`discount_*` 等）は take-3 版を踏襲する。

---

## 設計上の判断

### 1. Database per Service パターン

**判断**: 各マイクロサービスが専用のデータベースを持つ。コンテキスト間のデータ参照には DB 外部キー制約を設けず、業務キー（`booking_id`、`voyage_number` 等）による論理参照とする。

**根拠**: マイクロサービスアーキテクチャの基本原則として、各サービスのデータ独立性を確保する。整合性はドメインイベント（RabbitMQ + Spring Cloud Stream）で結果整合性として保証する。

### 2. `location` テーブルの各 DB への配置

**判断**: 共有カーネルである `Location`（UN/LOCODE）を各サービスの DB にローカルコピーとして配置する。

**根拠**: Database per Service パターンでは、サービス間で DB テーブルを共有することは禁止される。`location` は参照データ（マスタ）であり、変更頻度が極めて低い。初期データは共通の Flyway シードスクリプトから投入する。

### 3. サロゲートキーと業務キーの併用

**判断**: 全テーブルに `BIGSERIAL` のサロゲートキー（`id`）を設け、業務上の識別子には `UNIQUE` 制約を付与する。

**根拠**: DB 内の外部キー参照を `BIGINT` に統一することでインデックス効率が向上する。サービス間の論理参照は業務キー（文字列）を使用する。

### 4. 金額の表現（`INTEGER` + `VARCHAR(3)`）

**判断**: 金額を `INTEGER`（最小通貨単位）と `VARCHAR(3)`（ISO 4217 通貨コード）の 2 カラムで表現する。

**根拠**: 浮動小数点演算による精度誤差を排除する。ドメインモデルの `Money` 値オブジェクトに対応する。

### 5. 列挙値のカラム型（`VARCHAR(30)`）

**判断**: 列挙型カラムは `VARCHAR(30)` で表現し、PostgreSQL の `ENUM` 型は使用しない。

**根拠**: `ENUM` 型は値の追加・変更にスキーマ ALTER が必要でリスクが高い。`VARCHAR` + CHECK 制約なら H2 との互換性も維持できる。

### 6. コンテキスト間の結果整合性

**判断**: コンテキスト間の整合性はドメインイベント経由の結果整合性で保証する。DB レベルの分散トランザクション（2PC）は使用しない。

**根拠**: 貨物輸送業務の特性上、数秒〜数十秒の遅延は許容される。

### 7. 監査カラムの全テーブル付与

**判断**: `created_at`・`updated_at` を全テーブルに `NOT NULL DEFAULT NOW()` で付与する（`user_roles`・`customs_status_history` 等の追記専用テーブルを除く）。

**根拠**: 国際貨物輸送は規制上の監査要件が高い。H2 との互換性を優先してアプリケーション側で更新を制御する。

### 8. キャンセル申請を独立テーブルにする（take-7 追加）

**判断**: キャンセル申請は `cargo` のカラムではなく `cancellation_request` テーブル（1 対多）で管理する。

**根拠**: 却下後の再申請があり得るため履歴が必要（UC22 の受け入れ基準「申請・承認・却下の履歴が参照できる」）。申請時の予約状態（`booking_status_at_request`）を保存することでキャンセル料率の算定根拠を残す。集約としては Cargo 集約内エンティティであり、同一 DB・同一トランザクションで状態遷移と整合させる。

### 9. 通関申告の独立テーブル化と履歴の追記専用管理（take-7 変更）

**判断**: `customs_declaration` は `handling_activity` への FK を持たず、`booking_id` / `tracking_number` の論理参照とする。状態変更履歴 `customs_status_history` は UPDATE しない追記専用テーブルとする。

**根拠**: 通関申告は荷役作業とライフサイクルが独立している（ドメインモデル設計の集約昇格判断）。履歴は監査証跡であり、改変を防ぐため追記専用とする（`updated_at` を持たない）。

### 10. アカウント保護カラムの永続化（take-7 追加）

**判断**: 失敗回数（`failed_attempts`）とロック期限（`locked_until`）を `users` テーブルのカラムとして永続化する。ログや履歴からの再導出は行わない。

**根拠**: 集約状態を履歴から再導出すると、クロスリクエストで誤復帰する偽の安全網になる。ロック判定に必要な状態は必ずカラムに永続化する。

---

## Flyway マイグレーション方針

### サービス別マイグレーション管理

各サービスが独自のマイグレーションスクリプトを管理する。

```
apps/backend/
├── authms/
│   └── src/main/resources/db/migration/
│       ├── V1__init.sql               # スキーマ初期化
│       ├── V2__init_auth.sql          # users, user_roles, auth_audit_log
│       ├── V3__seed_users.sql         # 初期ユーザーデータ
│       └── V4__seed_disabled_user.sql # 無効化アカウント（US31 の動作確認用）
│
├── bookingms/
│   └── src/main/resources/db/migration/
│       ├── V1__init_booking.sql       # location, shipper, cargo, leg
│       ├── V2__seed_locations.sql     # UN/LOCODE マスタ
│       ├── V3__add_estimate.sql       # estimate, route_candidate
│       └── V4__add_cancellation.sql   # cancellation_request
│
├── routingms/
│   └── src/main/resources/db/migration/
│       ├── V1__init_routing.sql       # location, voyage, carrier_movement
│       ├── V2__seed_locations.sql     # UN/LOCODE マスタ（共通）
│       └── V3__seed_voyages.sql       # 初期航海データ
│
├── trackingms/
│   └── src/main/resources/db/migration/
│       ├── V1__init_tracking.sql      # location, tracking_*
│       └── V2__seed_locations.sql     # UN/LOCODE マスタ（共通）
│
├── handlingms/
│   └── src/main/resources/db/migration/
│       ├── V1__init_handling.sql      # location, handling_activity
│       ├── V2__seed_locations.sql     # UN/LOCODE マスタ（共通）
│       └── V3__add_customs.sql        # customs_declaration, customs_status_history
│
└── billingms/
    └── src/main/resources/db/migration/
        └── V1__init_billing.sql       # invoice, invoice_line_item, payment
```

### マイグレーションルール

- バージョン番号はサービスごとに独立した連番とし、番号の欠番を作らない
- 既存マイグレーションファイルの編集は禁止（Flyway チェックサム検証）
- `location` テーブルのシードスクリプトは共通テンプレートからコピーして各サービスに配置する
- 本番（PostgreSQL）と開発環境・テスト（H2）で同一マイグレーションスクリプトを使用するため、**方言固有の構文を書かない**。全マイグレーション SQL は CI で H2 / PostgreSQL の両方に対して実行できることを確認する（方言差は両方向に起きる）
- テストデータの採番はシーケンス等の本番経路を使う（MAX+1 の自前採番は UNIQUE 制約衝突の原因になる）

### 共通シードデータ（`location`）

```sql
-- 共通 UN/LOCODE シードデータ（各サービスの V2__seed_locations.sql にコピー）
INSERT INTO location (unlocode, name, country_code, time_zone) VALUES
    ('JPTYO', 'Tokyo',      'JP', 'Asia/Tokyo'),
    ('JPOSA', 'Osaka',      'JP', 'Asia/Tokyo'),
    ('JPNGO', 'Nagoya',     'JP', 'Asia/Tokyo'),
    ('CNSHA', 'Shanghai',   'CN', 'Asia/Shanghai'),
    ('CNHKG', 'Hong Kong',  'CN', 'Asia/Hong_Kong'),
    ('KRPUS', 'Busan',      'KR', 'Asia/Seoul'),
    ('SGSIN', 'Singapore',  'SG', 'Asia/Singapore'),
    ('USNYC', 'New York',   'US', 'America/New_York'),
    ('USLAX', 'Los Angeles','US', 'America/Los_Angeles'),
    ('NLRTM', 'Rotterdam',  'NL', 'Europe/Amsterdam'),
    ('DEHAM', 'Hamburg',    'DE', 'Europe/Berlin'),
    ('GBFXT', 'Felixstowe', 'GB', 'Europe/London'),
    ('AUMEL', 'Melbourne',  'AU', 'Australia/Melbourne');
```

---

## 参照

- [要件定義書](../requirements/requirements_definition.md)
- [ドメインモデル設計](domain-model.md)
- [バックエンドアーキテクチャ設計](architecture_backend.md)
- [データモデル設計ガイド](../reference/データモデル設計ガイド.md)
