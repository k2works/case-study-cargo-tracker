---
title: データモデル設計 - 国際貨物輸送管理システム
description: Axon Framework 5 の Event Sourcing + CQRS に基づく Database per Service データモデル設計。Event Store、Read Model（Projection）、Saga Store、Auth DB の各データ構造を ER 図とテーブル定義で示す。
published: true
date: 2026-05-21T00:00:00.000Z
tags: design, data-model, er, axon-5, cqrs, event-sourcing, postgresql
---

# データモデル設計 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムの永続化データモデルを定義する。Axon Framework 5 の CQRS + Event Sourcing アーキテクチャに基づき、データは次の 4 種類に分かれる。

| 種別 | 役割 | 永続化先 | 管理主体 |
| :--- | :--- | :--- | :--- |
| **Event Store** | すべての状態変更を不変イベント列として保持する真実の単一情報源 | Axon Server（EBS / EFS） | Axon Server |
| **Saga Store** | 実行中の Saga インスタンスの関連付け・状態 | Axon Server | Axon Server |
| **Read Model（Projection）** | クエリ最適化された読み取り用の状態 | 各マイクロサービス専用の PostgreSQL | 各マイクロサービス |
| **状態 DB（Auth のみ）** | Event Sourcing 適用外の CRUD データ | `auth_db`（PostgreSQL） | Auth Service |

ドメインモデル（[`domain-model.md`](domain-model.md)）との整合を保ち、Aggregate はイベント列として Event Store に保存され、Read Model は `@EventHandler` で更新される。

## 概念データモデル

業務概念レベルでのエンティティとリレーションシップ。コンテキスト境界を越える参照は識別子値（値オブジェクト）で表現する。

```plantuml
@startuml
title 概念データモデル

skinparam linetype ortho
hide circle

entity "荷主\n(Shipper)" as shipper {
  * 荷主ID
  --
  種別（個人/法人）
  氏名/社名
  住所
  連絡先
  契約番号 (法人のみ)
  割引率 (法人のみ)
}

entity "荷受人\n(Consignee)" as consignee {
  * 荷受人ID
  --
  氏名
  連絡先
}

entity "見積\n(Quotation)" as quotation {
  * 見積ID
  --
  荷主ID
  出発地
  目的地
  到着期限
  貨物仕様
  概算料金
  有効期限
}

entity "予約（貨物）\n(Cargo)" as cargo {
  * 予約ID
  --
  荷主ID
  追跡番号
  経路仕様
  旅程
  貨物仕様
  予約状態
  経路状態
  概算料金
}

entity "航海\n(Voyage)" as voyage {
  * 航海番号
  --
  運送会社
  船名
  スケジュール（寄港地・日時の列）
  対応貨物種別
}

entity "追跡情報\n(TrackingActivity)" as tracking {
  * 追跡番号
  --
  予約ID
  現在状態
  現在位置
  推定到着日
  誤配送フラグ
}

entity "追跡例外\n(TrackingException)" as exception {
  * 例外ID
  --
  追跡番号
  例外種別
  発生日時
  発生場所
  対応状態
}

entity "荷役作業\n(HandlingActivity)" as handling {
  * 荷役活動ID
  --
  追跡番号
  作業種別
  作業日時
  作業場所
  航海番号 (任意)
  作業員ID
  引取確認 (CLAIM のみ)
}

entity "請求書\n(Invoice)" as invoice {
  * 請求書ID
  --
  予約ID
  荷主ID
  基本料金
  割引額
  調整額
  請求金額
  精算状態
  支払期限
  入金日時
}

entity "ユーザー\n(User)" as user {
  * ユーザーID
  --
  ユーザー名
  メール
  パスワードハッシュ
  状態
  ロール
}

shipper ||--o{ quotation : 依頼
shipper ||--o{ cargo : 予約者
shipper ||--o{ invoice : 請求先
cargo ||--o| tracking : 追跡対象
cargo ||--o| invoice : 精算対象
cargo }o--|| voyage : 旅程の各 Leg で参照
tracking ||--o{ exception : 例外発生
tracking ||--o{ handling : 関連作業
consignee }o--o{ handling : 引取で確認

note right of shipper
  Booking Context
end note

note right of voyage
  Routing Context
end note

note right of tracking
  Tracking Context
end note

note right of handling
  Handling Context
end note

note right of invoice
  Billing Context
end note

note right of user
  Auth Context
end note
@enduml
```

## 物理データベース構成（Database per Service）

各マイクロサービスは専用の PostgreSQL データベースを持つ。Event Store のみが横断的に Axon Server に集約される。

| サービス | データベース名 | 用途 | 主要テーブル |
| :--- | :--- | :--- | :--- |
| Axon Server | （Axon 内部ストレージ） | Event Store | `domain_event_entry`, `snapshot_event_entry` |
| authms | `auth_db` | 認証・認可（CRUD）+ Axon Token Store | `users`, `roles`, `user_roles` + `token_entry`, `saga_entry`, `association_value_entry` |
| bookingms | `booking_read_db` | 予約 Read Model + Axon Token / Saga Store | `cargo_summary`, `cargo_leg`, `shipper`, `quotation`, `quotation_candidate` + `token_entry`, `saga_entry`, `association_value_entry` |
| routingms | `routing_read_db` | 航海 Read Model + 経路設計依頼（cross-service）+ Axon Token Store | `voyage`, `carrier_movement`, `voyage_accepted_cargo_type`, `route_design_request` + `token_entry`, `saga_entry`, `association_value_entry` |
| trackingms | `tracking_read_db` | 追跡 Read Model + Axon Token Store | `tracking_summary`, `tracking_event`, `tracking_exception` + `token_entry`, `saga_entry`, `association_value_entry` |
| handlingms | `handling_read_db` | 荷役 Read Model + Axon Token Store | `handling_activity`, `claim_verification` + `token_entry`, `saga_entry`, `association_value_entry` |
| billingms | `billing_read_db` | 精算 Read Model + Axon Token Store | `invoice`, `payment` + `token_entry`, `saga_entry`, `association_value_entry` |

> **データアクセス方式**: 本プロジェクトは **MyBatis** を採用する。Read Model / Auth DB のすべてのテーブルは MyBatis Mapper（XML / Annotation）でアクセスする。Axon の `JdbcTokenStore` / `JdbcSagaStore` は標準実装を使用し、Read Model と同一 DataSource を共有することで `@EventHandler` 内の Projection 更新と Token 更新が **同一 JDBC トランザクション** で処理される。

すべてのテーブルに共通で監査カラムを持たせる。

| カラム | 型 | 用途 |
| :--- | :--- | :--- |
| `created_at` | `TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` | 作成日時 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` | 更新日時 |
| `version` | `BIGINT NOT NULL DEFAULT 0` | 楽観的並行制御用バージョン番号 |

## Event Store（Axon Server 管理）

Axon Server がデフォルトで管理するスキーマ。マイクロサービスのコードはこのテーブルへ直接 SQL を発行せず、Axon の `EventStore` API を介してアクセスする。

```plantuml
@startuml
title Event Store スキーマ（Axon Server 内部、参考）

entity "domain_event_entry" as dee {
  * **global_index**: BIGSERIAL <<PK>>
  --
  event_identifier: VARCHAR(255) UNIQUE
  aggregate_identifier: VARCHAR(255)
  sequence_number: BIGINT
  type: VARCHAR(255)         ' Aggregate 種別
  payload_type: VARCHAR(255) ' Event クラス名
  payload_revision: VARCHAR(255)
  payload: BYTEA
  meta_data: BYTEA
  time_stamp: VARCHAR(255)
}

entity "snapshot_event_entry" as see {
  * **aggregate_identifier**: VARCHAR(255) <<PK>>
  * **sequence_number**: BIGINT <<PK>>
  --
  type: VARCHAR(255)
  payload_type: VARCHAR(255)
  payload_revision: VARCHAR(255)
  payload: BYTEA
  meta_data: BYTEA
  event_identifier: VARCHAR(255)
  time_stamp: VARCHAR(255)
}

entity "saga_entry" as se {
  * **saga_id**: VARCHAR(255) <<PK>>
  --
  saga_type: VARCHAR(255)
  revision: VARCHAR(255)
  serialized_saga: BYTEA
}

entity "association_value_entry" as ave {
  * **id**: BIGSERIAL <<PK>>
  --
  association_key: VARCHAR(255)
  association_value: VARCHAR(255)
  saga_id: VARCHAR(255)
  saga_type: VARCHAR(255)
}

entity "token_entry" as te {
  * **processor_name**: VARCHAR(255) <<PK>>
  * **segment**: INTEGER <<PK>>
  --
  token: BYTEA
  token_type: VARCHAR(255)
  timestamp: VARCHAR(255)
  owner: VARCHAR(255)
}

dee ||--o{ see : スナップショット元
se ||--o{ ave : 関連付け
@enduml
```

### 運用上の注意

- **アクセス方式**: コードからは `EventStore` / `EventGateway` を介し、SQL 直接アクセス禁止
- **スナップショット**: イベント数が一定（既定 50 件）を超えた集約には自動でスナップショットを取得
- **トークン管理**: 各 `@EventHandler`（Tracking Event Processor）の進捗を `token_entry` で記録
- **再生**: Token をリセットすると Projection を Event Store から再構築可能（H1 / M11 / M12 対応で重要）
- **バックアップ**: EBS スナップショット + S3 エクスポート（インフラ設計参照）

## Read Model 詳細設計

### Booking Read Model（`booking_read_db`）

```plantuml
@startuml
title booking_read_db ER 図

hide circle
skinparam linetype ortho

entity "shipper" as shipper {
  * **shipper_id**: VARCHAR(36) <<PK>>
  --
  shipper_type: VARCHAR(16) NOT NULL  ' INDIVIDUAL / CORPORATE
  name: VARCHAR(200) NOT NULL
  address_line1: VARCHAR(200) NOT NULL
  address_line2: VARCHAR(200)
  city: VARCHAR(100) NOT NULL
  country_code: VARCHAR(2) NOT NULL
  postal_code: VARCHAR(20)
  email: VARCHAR(255) NOT NULL <<UNIQUE>>
  phone: VARCHAR(30) NOT NULL
  contract_number: VARCHAR(50)
  discount_rate: NUMERIC(4,3)         ' 法人のみ 0.000-0.300
  active: BOOLEAN NOT NULL DEFAULT TRUE
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "cargo_summary" as cargo {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  shipper_id: VARCHAR(36) NOT NULL <<FK>>
  tracking_number: VARCHAR(25) <<UNIQUE>>
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  arrival_deadline: DATE NOT NULL
  cargo_type: VARCHAR(16) NOT NULL    ' GENERAL / HAZARDOUS / REFRIGERATED
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
  booking_status: VARCHAR(20) NOT NULL  ' PRELIMINARY / ROUTING / ... / CANCELLED
  routing_status: VARCHAR(16) NOT NULL  ' NOT_ROUTED / ROUTED / MISROUTED
  estimated_amount: NUMERIC(14,2)
  estimated_currency: VARCHAR(3)
  last_event_at: TIMESTAMPTZ
  route_notified_at: TIMESTAMPTZ      ' 確定経路の荷主通知日時（US12 / IT4 V7）
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "cargo_leg" as leg {
  * **booking_id**: VARCHAR(36) <<PK>> <<FK>>
  * **leg_seq**: INTEGER <<PK>>
  --
  voyage_number: VARCHAR(20) NOT NULL
  load_unlocode: VARCHAR(5) NOT NULL
  unload_unlocode: VARCHAR(5) NOT NULL
  load_at: TIMESTAMPTZ NOT NULL
  unload_at: TIMESTAMPTZ NOT NULL
}

entity "quotation" as quotation {
  * **quotation_id**: VARCHAR(36) <<PK>>
  --
  shipper_id: VARCHAR(36) NOT NULL <<FK>>
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  arrival_deadline: DATE NOT NULL
  cargo_type: VARCHAR(16) NOT NULL
  weight_kg: NUMERIC(12,2)
  estimated_amount: NUMERIC(14,2)
  estimated_currency: VARCHAR(3)
  valid_until: DATE NOT NULL
  status: VARCHAR(16) NOT NULL ' DRAFT / OFFERED / ACCEPTED / EXPIRED
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "quotation_candidate" as candidate {
  * **quotation_id**: VARCHAR(36) <<PK>> <<FK>>
  * **candidate_seq**: INTEGER <<PK>>
  --
  estimated_days: INTEGER NOT NULL
  estimated_cost: NUMERIC(14,2) NOT NULL
  estimated_currency: VARCHAR(3) NOT NULL
  itinerary_summary: TEXT
}

entity "consignee" as consignee {
  * **consignee_id**: VARCHAR(36) <<PK>>
  --
  name: VARCHAR(200) NOT NULL
  email: VARCHAR(255)
  phone: VARCHAR(30)
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
}

entity "cargo_consignee" as cc {
  * **booking_id**: VARCHAR(36) <<PK>> <<FK>>
  * **consignee_id**: VARCHAR(36) <<PK>> <<FK>>
}

shipper ||--o{ cargo : "1..*"
shipper ||--o{ quotation : "1..*"
quotation ||--|{ candidate : "1..*"
cargo ||--|{ leg : "1..*"
cargo ||--o| cc : "0..1"
consignee ||--o{ cc : "0..*"

note right of cargo
  cargo_summary は Cargo Aggregate の Read Model。
  Event Store のイベント列を CargoProjectionsEventHandler が
  購読して更新する。
end note

note right of quotation
  Quotation は予約前段階の Read Model。
  受け入れ時に Cargo を生成して関連付ける。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `shipper` | `UNIQUE(email)` | 重複登録チェック（UC02 拡張 4a） |
| `shipper` | `CHECK(discount_rate BETWEEN 0 AND 0.3)` | 割引率 0〜30% の制約（US03） |
| `shipper` | `CHECK(shipper_type = 'CORPORATE' OR (contract_number IS NULL AND discount_rate IS NULL))` | 個人荷主は契約情報を持たない |
| `cargo_summary` | `UNIQUE(tracking_number)` | 追跡番号の一意性 |
| `cargo_summary` | `INDEX(shipper_id)`, `INDEX(booking_status)`, `INDEX(routing_status)` | 一覧画面の絞り込み |
| `cargo_summary` | `CHECK(arrival_deadline >= CURRENT_DATE - INTERVAL '5 years')` | 不正な期限の検出 |
| `cargo_leg` | `INDEX(voyage_number)` | 航海変更時の影響範囲特定 |
| `quotation` | `INDEX(shipper_id, status)` | 荷主別の見積検索 |
| `cargo_summary` | `INDEX(created_at DESC)` | 一覧のデフォルトソート・LIMIT/OFFSET ページネーション（ADR-0008、IT3 で Flyway 追加予定） |

### Routing Read Model（`routing_read_db`）

```plantuml
@startuml
title routing_read_db ER 図

hide circle
skinparam linetype ortho

entity "voyage" as voyage {
  * **voyage_number**: VARCHAR(20) <<PK>>
  --
  carrier_code: VARCHAR(10) NOT NULL
  carrier_name: VARCHAR(200) NOT NULL
  ship_name: VARCHAR(200) NOT NULL
  departure_date: TIMESTAMPTZ NOT NULL
  arrival_date: TIMESTAMPTZ NOT NULL
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  status: VARCHAR(16) NOT NULL ' SCHEDULED / DEPARTED / ARRIVED / CANCELLED
  registered_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "carrier_movement" as movement {
  * **voyage_number**: VARCHAR(20) <<PK>> <<FK>>
  * **movement_seq**: INTEGER <<PK>>
  --
  departure_unlocode: VARCHAR(5) NOT NULL
  arrival_unlocode: VARCHAR(5) NOT NULL
  departure_time: TIMESTAMPTZ NOT NULL
  arrival_time: TIMESTAMPTZ NOT NULL
}

entity "voyage_accepted_cargo_type" as cargotype {
  * **voyage_number**: VARCHAR(20) <<PK>> <<FK>>
  * **cargo_type**: VARCHAR(16) <<PK>>  ' GENERAL / HAZARDOUS / REFRIGERATED
}

entity "location_master" as locmaster {
  * **unlocode**: VARCHAR(5) <<PK>>
  --
  port_name: VARCHAR(200) NOT NULL
  country_code: VARCHAR(2) NOT NULL
  latitude: NUMERIC(8,5)
  longitude: NUMERIC(8,5)
  active: BOOLEAN NOT NULL DEFAULT TRUE
}

entity "route_design_request" as rdr {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  arrival_deadline: DATE NOT NULL
  cargo_type: VARCHAR(16) NOT NULL ' GENERAL / HAZARDOUS / REFRIGERATED
  status: VARCHAR(16) NOT NULL ' PENDING → ROUTE_SELECTED（US09）→ ASSIGNED（US11、M4）
  requested_at: TIMESTAMPTZ NOT NULL
}

voyage ||--|{ movement : "1..*"
voyage ||--o{ cargotype : "0..*"
movement }o--|| locmaster : "出発港"
movement }o--|| locmaster : "到着港"

note right of voyage
  Voyage Aggregate の Read Model。
  経路候補算出のために OptimalRouteService が参照する。
end note

note right of locmaster
  UN/LOCODE マスタは Routing が
  集中管理する。国際標準データのため
  他コンテキストは値オブジェクト Location で参照する。
end note

note bottom of rdr
  bookingms の RouteDesignRequestedEvent（cross-service、
  ADR-0009）を Kafka tracking モードで購読して記録する
  経路設計待ちリスト。経路設計者ワークベンチ（IT4/US08）の入力。
  tracking 再処理に備え booking_id 単位で冪等に登録する。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `voyage` | `INDEX(origin_unlocode, destination_unlocode, departure_date)` | 経路検索の高速化（UC05） |
| `voyage` | `CHECK(arrival_date > departure_date)` | 日付整合性（UC19 拡張 3b） |
| `carrier_movement` | `CHECK(arrival_time > departure_time)` | 移動ごとの整合性 |
| `voyage_accepted_cargo_type` | `INDEX(cargo_type)` | 貨物種別での絞り込み |
| `location_master` | `INDEX(country_code)` | 国別のマスタ管理 |

### Tracking Read Model（`tracking_read_db`）

```plantuml
@startuml
title tracking_read_db ER 図

hide circle
skinparam linetype ortho

entity "tracking_summary" as ts {
  * **tracking_number**: VARCHAR(25) <<PK>>
  --
  booking_id: VARCHAR(36) NOT NULL <<UNIQUE>>
  current_status: VARCHAR(20) NOT NULL ' NOT_RECEIVED / RECEIVED / LOADED / IN_TRANSIT / UNLOADED / AWAITING_CLAIM / DELIVERED / MISROUTED / EXCEPTION
  current_unlocode: VARCHAR(5)
  current_voyage_number: VARCHAR(20)
  estimated_arrival: TIMESTAMPTZ
  misrouted: BOOLEAN NOT NULL DEFAULT FALSE
  last_event_at: TIMESTAMPTZ
  delivered_at: TIMESTAMPTZ          ' 配送完了時刻（JWT 有効期限計算に使用: ADR-0013）
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "tracking_event" as te {
  * **event_id**: BIGSERIAL <<PK>>
  --
  tracking_number: VARCHAR(25) NOT NULL <<FK>>
  occurred_at: TIMESTAMPTZ NOT NULL
  recorded_at: TIMESTAMPTZ NOT NULL
  event_type: VARCHAR(40) NOT NULL ' TRACKING_INITIALIZED / STATUS_UPDATED / EXCEPTION_REGISTERED / ...
  transport_status: VARCHAR(20)
  unlocode: VARCHAR(5)
  voyage_number: VARCHAR(20)
  handling_type: VARCHAR(16)         ' 関連する Handling の種別
  source: VARCHAR(16)                ' 記録元: HANDLING / MANUAL / SYSTEM（IT6 追加）
  description: TEXT
}

entity "tracking_exception" as ex {
  * **exception_id**: VARCHAR(36) <<PK>>
  --
  tracking_number: VARCHAR(25) NOT NULL <<FK>>
  exception_type: VARCHAR(16) NOT NULL ' DELAY / DAMAGE / LOSS
  occurred_at: TIMESTAMPTZ NOT NULL
  occurred_unlocode: VARCHAR(5)
  description: TEXT NOT NULL
  response_status: VARCHAR(16) NOT NULL ' REPORTED / RESPONDING / RESOLVED
  resolution: TEXT
  resolved_at: TIMESTAMPTZ
  escalated: BOOLEAN NOT NULL DEFAULT FALSE
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
}

ts ||--|{ te : "1..*"
ts ||--o{ ex : "0..*"

note right of ts
  TrackingActivity Aggregate の Read Model。
  最新状態のみを保持する。
end note

note right of te
  追跡イベント履歴。
  UC15 の追跡情報照会で時系列表示に使用。
  挿入のみ・更新なし。
end note

note right of ex
  追跡例外（遅延・破損・紛失）の Read Model。
  LOSS の場合は escalated = TRUE で
  管理職向け escalation 通知トリガーとなる。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `tracking_summary` | `UNIQUE(booking_id)` | 予約と追跡の 1:1 |
| `tracking_summary` | `INDEX(current_status)`, `INDEX(misrouted)` | 例外監視ダッシュボード用 |
| `tracking_event` | `INDEX(tracking_number, occurred_at)` | 時系列照会の高速化（UC15） |
| `tracking_event` | `INDEX(event_type, recorded_at)` | 監査・分析クエリ |
| `tracking_exception` | `INDEX(tracking_number, response_status)` | 例外対応ダッシュボード |
| `tracking_exception` | `CHECK(resolved_at IS NULL OR response_status = 'RESOLVED')` | 解決時刻の整合性 |

### Handling Read Model（`handling_read_db`）

```plantuml
@startuml
title handling_read_db ER 図

hide circle
skinparam linetype ortho

entity "handling_activity" as ha {
  * **activity_id**: VARCHAR(36) <<PK>>
  --
  ' CargoSnapshot ACL の射影
  booking_id: VARCHAR(36) NOT NULL
  tracking_number: VARCHAR(25) NOT NULL
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  cargo_type: VARCHAR(16) NOT NULL
  ' 荷役作業本体
  handling_type: VARCHAR(16) NOT NULL ' RECEIVE / LOAD / UNLOAD / CLAIM / CUSTOMS
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

note right of ha
  HandlingActivity Aggregate の Read Model。
  CargoSnapshot ACL の内容をフラットに展開している。
  Booking Context の cargo_summary を直接 JOIN しない。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `handling_activity` | `INDEX(tracking_number, occurred_at)` | 追跡番号別の作業履歴 |
| `handling_activity` | `INDEX(voyage_number)` | 航海単位での作業集計 |
| `handling_activity` | `INDEX(handler_id)` | 作業員別の実績 |
| `handling_activity` | `UNIQUE(tracking_number, handling_type, unlocode, date_trunc('minute', occurred_at))` | 重複登録防止（5 分粒度） |
| `claim_verification` | `CHECK(signature_ref IS NOT NULL OR confirmation_code IS NOT NULL)` | 引取時の確認手段必須（US16） |

### Billing Read Model（`billing_read_db`）

```plantuml
@startuml
title billing_read_db ER 図

hide circle
skinparam linetype ortho

entity "invoice" as inv {
  * **invoice_id**: VARCHAR(36) <<PK>>
  --
  booking_id: VARCHAR(36) NOT NULL <<UNIQUE>>
  shipper_id: VARCHAR(36) NOT NULL
  basic_amount: NUMERIC(14,2) NOT NULL
  discount_amount: NUMERIC(14,2) NOT NULL DEFAULT 0
  adjustment_amount: NUMERIC(14,2) NOT NULL DEFAULT 0
  total_amount: NUMERIC(14,2) NOT NULL
  currency: VARCHAR(3) NOT NULL
  billing_status: VARCHAR(16) NOT NULL ' PENDING / CALCULATED / INVOICED / PAID / OVERDUE / CANCELLED
  invoice_number: VARCHAR(30) <<UNIQUE>>
  payment_due: DATE
  paid_at: TIMESTAMPTZ
  cancellation_reason: TEXT
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "invoice_line" as line {
  * **invoice_id**: VARCHAR(36) <<PK>> <<FK>>
  * **line_seq**: INTEGER <<PK>>
  --
  line_type: VARCHAR(20) NOT NULL ' BASIC / DISCOUNT / ADJUSTMENT / SURCHARGE
  description: VARCHAR(255) NOT NULL
  amount: NUMERIC(14,2) NOT NULL
  reason_code: VARCHAR(40)
}

entity "payment" as pay {
  * **payment_id**: VARCHAR(36) <<PK>>
  --
  invoice_id: VARCHAR(36) NOT NULL <<FK>>
  paid_amount: NUMERIC(14,2) NOT NULL
  currency: VARCHAR(3) NOT NULL
  paid_at: TIMESTAMPTZ NOT NULL
  payment_method: VARCHAR(40)
  external_reference: VARCHAR(100) ' 決済機関の取引番号
}

inv ||--|{ line : "1..*"
inv ||--o{ pay : "0..*（複数回入金）"

note right of inv
  Invoice Aggregate の Read Model。
  total_amount = basic_amount - discount_amount + adjustment_amount。
  CHECK 制約で整合性を確保する。
end note

note right of line
  料金内訳明細。
  経理担当者が請求書 PDF を生成する際の入力。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `invoice` | `UNIQUE(booking_id)` | 1 予約 1 請求 |
| `invoice` | `UNIQUE(invoice_number) WHERE invoice_number IS NOT NULL` | 請求書番号の一意性（発行後のみ） |
| `invoice` | `INDEX(shipper_id, billing_status)` | 荷主別の請求一覧 |
| `invoice` | `INDEX(billing_status, payment_due)` | 督促対象の抽出 |
| `invoice` | `CHECK(total_amount = basic_amount - discount_amount + adjustment_amount)` | 金額の整合性 |
| `invoice` | `CHECK(discount_amount >= 0 AND adjustment_amount >= 0 AND basic_amount >= 0)` | 非負制約 |
| `payment` | `INDEX(invoice_id)` | 請求書ごとの入金履歴 |

### Auth DB（`auth_db`）

Event Sourcing 適用外。通常の状態 DB として CRUD で運用する。

```plantuml
@startuml
title auth_db ER 図

hide circle
skinparam linetype ortho

entity "users" as u {
  * **user_id**: VARCHAR(36) <<PK>>
  --
  username: VARCHAR(60) NOT NULL <<UNIQUE>>
  email: VARCHAR(255) NOT NULL <<UNIQUE>>
  password_hash: VARCHAR(255) NOT NULL
  status: VARCHAR(16) NOT NULL ' ACTIVE / LOCKED / DEACTIVATED
  failed_attempts: INTEGER NOT NULL DEFAULT 0
  lock_until: TIMESTAMP NULL  ' 5 回連続失敗で 30 分ロック（NULL = 未ロック、IT2 / V006）
  last_login_at: TIMESTAMPTZ
  password_changed_at: TIMESTAMPTZ
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
  version: BIGINT
}

entity "roles" as r {
  * **role_id**: VARCHAR(36) <<PK>>
  --
  name: VARCHAR(40) NOT NULL <<UNIQUE>> ' ROLE_SHIPPER / ROLE_SALES / ROLE_ROUTING / ROLE_TRACKER / ROLE_HANDLER / ROLE_ACCOUNTANT / ROLE_ADMIN
  description: VARCHAR(255)
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
}

entity "user_roles" as ur {
  * **user_id**: VARCHAR(36) <<PK>> <<FK>>
  * **role_id**: VARCHAR(36) <<PK>> <<FK>>
  --
  assigned_at: TIMESTAMPTZ
}

entity "role_permissions" as rp {
  * **role_id**: VARCHAR(36) <<PK>> <<FK>>
  * **permission**: VARCHAR(80) <<PK>>
  --
  granted_at: TIMESTAMPTZ
}

entity "user_sessions" as us {
  * **session_id**: VARCHAR(64) <<PK>>
  --
  user_id: VARCHAR(36) NOT NULL <<FK>>
  jwt_id: VARCHAR(64) NOT NULL <<UNIQUE>>
  issued_at: TIMESTAMPTZ NOT NULL
  expires_at: TIMESTAMPTZ NOT NULL
  revoked: BOOLEAN NOT NULL DEFAULT FALSE
  ip_address: VARCHAR(45)
  user_agent: VARCHAR(255)
}

u ||--|{ ur : "1..*"
r ||--o{ ur : "0..*"
r ||--o{ rp : "0..*"
u ||--o{ us : "0..*"

note right of u
  状態 DB。Event Sourcing は適用しない。
  失敗回数（failed_attempts）と lock_until でアカウントロック判定（IT2 / US00-r1）。
  5 回連続失敗で lock_until = NOW() + 30 分、成功時に failed_attempts = 0 / lock_until = NULL にリセット。
end note

note right of us
  発行済 JWT の管理（任意）。
  ログアウトや強制失効が必要な場合に使用。
  ステートレス JWT 運用なら省略可能。
end note
@enduml
```

#### インデックス・制約

| テーブル | インデックス・制約 | 用途 |
| :--- | :--- | :--- |
| `users` | `UNIQUE(username)`, `UNIQUE(email)` | 一意性保証 |
| `users` | `INDEX(status)` | アクティブユーザー検索 |
| `user_sessions` | `INDEX(user_id, revoked)`, `INDEX(expires_at)` | 失効処理・セッション検索 |

## ドメインモデルとのマッピング

ドメインモデル（[`domain-model.md`](domain-model.md)）の集約・値オブジェクトと物理テーブルの対応。

| コンテキスト | Aggregate / 値オブジェクト | Event Store | Read Model テーブル |
| :--- | :--- | :--- | :--- |
| Booking | `Cargo`（集約） | Axon Server | `cargo_summary`, `cargo_leg`, `cargo_consignee` |
| Booking | `Shipper`（集約） | Axon Server | `shipper` |
| Booking | `Consignee`（エンティティ） | （Cargo の一部） | `consignee` |
| Booking | `Quotation`（集約） | Axon Server | `quotation`, `quotation_candidate` |
| Routing | `Voyage`（集約） | Axon Server | `voyage`, `carrier_movement`, `voyage_accepted_cargo_type` |
| Routing | `Location`（VO・マスタ） | - | `location_master`（マスタ） |
| Tracking | `TrackingActivity`（集約） | Axon Server | `tracking_summary`, `tracking_event` |
| Tracking | `TrackingException`（エンティティ） | （TrackingActivity の一部） | `tracking_exception` |
| Handling | `HandlingActivity`（集約） | Axon Server | `handling_activity`, `handling_itinerary_snapshot`, `claim_verification` |
| Billing | `Invoice`（集約） | Axon Server | `invoice`, `invoice_line`, `payment` |
| Auth | `User`（集約） | - | `users`, `user_roles`, `user_sessions` |
| Auth | `Role`（エンティティ） | - | `roles`, `role_permissions` |

## 命名規則

| 対象 | 規則 | 例 |
| :--- | :--- | :--- |
| データベース名 | `<service>_<purpose>_db` 形式（snake_case） | `booking_read_db`, `auth_db` |
| テーブル名 | コンテキスト内で一意な単数形 snake_case | `cargo_summary`, `tracking_event`, `invoice` |
| カラム名 | snake_case。意味のある業務用語 | `booking_id`, `arrival_deadline`, `discount_rate` |
| 主キー | `<entity>_id`（識別子は VARCHAR(36) で UUID 文字列） | `booking_id`, `voyage_number`（自然キー） |
| 外部キー | 参照先のカラム名と同一 | `shipper_id` → `shipper(shipper_id)` |
| 監査カラム | 全テーブル共通 | `created_at`, `updated_at`, `version` |
| 状態カラム | `<entity>_status` または `status`、VARCHAR の列挙文字列 | `booking_status`, `billing_status` |

## マイグレーション戦略

| 種別 | ツール | 適用先 |
| :--- | :--- | :--- |
| Read Model スキーマ | **Flyway**（Versioned migrations） | 各マイクロサービスの起動時、`booking_read_db` 等 |
| Auth DB スキーマ | **Flyway** | authms 起動時 |
| Event Store スキーマ | Axon Server 自動管理 | - |
| Event Schema 進化 | **Axon Upcaster** | コードで配備 |

### Flyway ファイル命名規則

```
db/migration/
├── V001__create_cargo_summary.sql
├── V002__create_cargo_leg.sql
├── V003__create_shipper.sql
├── V010__add_index_to_cargo_summary.sql
└── V020__add_quotation_tables.sql
```

| 接頭辞 | 意味 |
| :--- | :--- |
| `V<num>__<desc>.sql` | 適用順序付きの変更（Versioned） |
| `R__<desc>.sql` | リピーティング（ビュー定義など） |

## 非機能要件への対応

| 観点 | 対応 |
| :--- | :--- |
| パフォーマンス | 検索キー（追跡番号・予約ID・荷主ID・状態）にインデックス、結合に外部キー、`tracking_event` は時系列インデックス |
| データ量 | `tracking_event` は時系列で増加するため、`occurred_at` 月単位のパーティショニングを将来検討 |
| バックアップ | Read Model は日次自動スナップショット（RDS）、Event Store は EBS スナップショット + S3 日次エクスポート（インフラ設計） |
| 災害復旧 | Read Model 破損時は Event Store からトークンリセットして再構築可能 |
| 監査 | Event Store のイベント列が監査ログを兼ねる（全変更が時系列で残る） |
| データ削除 | マスタは論理削除（`active = FALSE`）、トランザクションは物理削除しない（イベントは不変） |

## トレーサビリティ（UC ↔ テーブル）

| UC | 主に書き込まれるテーブル | 主に読み取られるテーブル |
| :--- | :--- | :--- |
| UC01 見積作成 | `quotation`, `quotation_candidate` | `voyage`, `location_master` |
| UC02 荷主登録 | `shipper` | `shipper`（重複チェック） |
| UC03 貨物予約登録 | `cargo_summary` | `shipper`, `quotation` |
| UC04 予約引き渡し | `cargo_summary`（status 更新） | - |
| UC05 航海検索 | - | `voyage`, `carrier_movement`, `voyage_accepted_cargo_type`, `location_master` |
| UC06 経路候補算出 | - | `voyage`, `carrier_movement` |
| UC07 経路選択・確定 | `cargo_summary`, `cargo_leg` | - |
| UC08 経路条件調整 | `cargo_summary` | - |
| UC09 経路情報紐付 | `cargo_summary`, `cargo_leg` | - |
| UC10 確定経路通知 | （外部 ACL） | `cargo_summary`, `cargo_leg`, `shipper` |
| UC11 予約確定 | `cargo_summary` | - |
| UC12 追跡番号発行 | `cargo_summary`, `tracking_summary` | - |
| UC13 荷役作業記録 | `handling_activity`, `claim_verification` | - |
| UC14 貨物状態更新 | `tracking_summary`, `tracking_event` | - |
| UC15 追跡情報照会 | - | `tracking_summary`, `tracking_event`, `tracking_exception` |
| UC16 例外処理 | `tracking_exception`, `tracking_summary`, `tracking_event` | - |
| UC17 輸送料金算出 | `invoice`, `invoice_line` | `cargo_summary`, `cargo_leg`, `handling_activity`, `shipper` |
| UC18 精算処理 | `invoice`, `payment` | `invoice` |
| UC19 航海登録 | `voyage`, `carrier_movement`, `voyage_accepted_cargo_type` | `voyage`（重複チェック） |

## 設計判断と推奨事項

### Event Store と Read Model の分離

- **Event Store は真実の単一情報源**。Read Model は再構築可能な「キャッシュ」と捉える
- スキーマ変更時、Read Model は気軽にドロップ＆再構築できる（Token リセット）

### 識別子の方針

- 集約識別子は **UUID 文字列（VARCHAR(36)）** を採用。Axon の `@AggregateIdentifier` と整合
- 例外的に `voyage_number` は自然キー（業界の運用上の値）
- 追跡番号は `TRK-` + 大文字英数 10 桁（推測困難な書式）

### 通貨の扱い

- `Money` を表すカラムは常に `NUMERIC(14,2)` + ISO 4217 の `VARCHAR(3)` 通貨カラムのペアで保持
- 集約内・テーブル内では通貨混在を許さない（CHECK 制約や Aggregate 不変条件で保証）

### 削除の方針

- マスタ（`shipper`, `voyage`, `location_master`）: 論理削除（`active = FALSE`）
- トランザクション（`cargo_summary`, `tracking_*`, `handling_*`, `invoice`）: 物理削除なし。状態遷移で表現（例: `CANCELLED`）
- Event Store のイベントは絶対に削除しない（監査要件）

### パーティショニング（将来）

- `tracking_event` が年間 1 千万件超を見込む場合、`occurred_at` の月単位レンジパーティショニングを導入
- 採用は実トラフィックの計測後

## 参照

- [要件定義書](../requirements/requirements_definition.md)
- [ビジネスユースケース](../requirements/business_usecase.md)
- [システムユースケース](../requirements/system_usecase.md)
- [ユーザーストーリー](../requirements/user_story.md)
- [バックエンドアーキテクチャ](architecture_backend.md)
- [ドメインモデル設計](domain-model.md)
- [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](../adr/0001-axon-framework-adoption.md)
- [データモデル設計ガイド](../reference/データモデル設計ガイド.md)
