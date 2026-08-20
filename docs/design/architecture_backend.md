---
title: バックエンドアーキテクチャ
description: 国際貨物輸送管理システムのバックエンドアーキテクチャ設計。DDD・ヘキサゴナル・CQRS パターンをマイクロサービスとして Spring Boot で実装する。
published: true
date: 2026-08-19T00:00:00.000Z
tags: architecture, backend, ddd, hexagonal, cqrs, microservices, spring-boot
---

# バックエンドアーキテクチャ - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムのバックエンドアーキテクチャを定義する。
Practical DDD in Enterprise Java（Chapter 5）のマイクロサービスアーキテクチャ思想（DDD・ヘキサゴナル・イベント駆動・CQRS）を継承しつつ、
Spring Boot / Java / Gradle を基盤とし、データアクセスには MyBatis を採用した現代的な実装とする。

本設計は take-3 のマイクロサービス設計を基礎とし、本プロジェクトの要件定義で追加された
通関手続き（UC21）・予約キャンセル承認（UC22）・誤配検知と経路再設計（US28）・アカウント保護（US31）を反映している。

## アーキテクチャパターン選択

### 業務領域カテゴリーの評価

| 評価軸 | 判定 | 根拠 |
| :--- | :--- | :--- |
| 業務領域カテゴリー | **中核の業務領域** | 国際貨物輸送は複雑なビジネスルール（経路設計、積み替え、通関、例外処理）を持つ |
| データ構造の複雑さ | **複雑** | エンティティ間の関係が多く、コンテキスト間でデータを共有・変換する必要がある |
| 特殊要件 | **あり** | 金額を扱う（Billing Context）、監査記録が必要（荷役履歴・通関・キャンセル承認）、状態遷移が厳密 |

### 選択したアーキテクチャパターン

上記評価から、以下の組み合わせを採用する。

- **ドメインモデル**: ビジネスルールをドメインオブジェクトにカプセル化し、手続き的なロジックを排除する
- **ポートとアダプター（ヘキサゴナルアーキテクチャ）**: ドメインを技術的関心事から独立させ、テスト容易性を確保する
- **CQRS（コマンドクエリ責務分離）**: Booking / Tracking の読み書き負荷特性の違いに対応し、クエリを読み取り最適化モデルで返す
- **マイクロサービス**: 各バウンデッドコンテキストを独立したデプロイ単位とし、スケーラビリティと独立性を確保する

Billing Context は `Money` 値オブジェクトによる金額管理を行うが、初期フェーズではイベントソーシングは適用しない。

## 全体アーキテクチャ

```plantuml
@startuml
title バックエンド全体アーキテクチャ（マイクロサービス）

package "Client Layer" {
  [Web Browser\n(React SPA)]
  [External System\n(Port Management / Customs)]
}

package "API Gateway (gatewayms)" {
  [Spring Cloud Gateway\n(@Route + JWT Filter)]
}

package "Auth Microservice (authms)" {
  [Auth REST Controller] as auth_rest
  [Domain Model\n(User, Role, AccountLock)] as auth_domain
  [MyBatis Repository] as auth_repo
  [JWT Provider] as jwt
}

package "Booking Microservice (bookingms)" {
  package "interfaces/" {
    [rest/ Controller\n(@RestController)] as booking_rest
    [events/ Handler\n(@EventListener)] as booking_events
  }
  package "application/internal/" {
    [commandservices/] as booking_cmd
    [queryservices/] as booking_qry
    [outboundservices/acl/] as booking_acl
  }
  package "domain/model/" {
    [aggregates/\n(Cargo, CancellationRequest)] as booking_agg
  }
  package "infrastructure/" {
    [repositories/\n(MyBatis)] as booking_repo
    [brokers/\n(RabbitMQ Publisher)] as booking_broker
  }
}

package "Routing Microservice (routingms)" {
  [REST Controller] as routing_rest
  [Domain Model\n(Voyage)] as routing_domain
  [MyBatis Repository] as routing_repo
}

package "Tracking Microservice (trackingms)" {
  [REST Controller] as tracking_rest
  [Event Subscriber] as tracking_sub
  [Domain Model\n(TrackingActivity)] as tracking_domain
  [MyBatis Repository] as tracking_repo
}

package "Handling Microservice (handlingms)" {
  [REST Controller] as handling_rest
  [Domain Model\n(HandlingActivity,\nCustomsDeclaration)] as handling_domain
  [MyBatis Repository] as handling_repo
  [RabbitMQ Publisher] as handling_broker
}

package "Billing Microservice (billingms)" {
  [REST Controller] as billing_rest
  [Event Subscriber] as billing_sub
  [Domain Model\n(Invoice)] as billing_domain
  [MyBatis Repository] as billing_repo
}

queue "RabbitMQ\n(Message Broker)" as MQ

database "auth_db\n(PostgreSQL)" as ADB
database "booking_db\n(PostgreSQL)" as BDB
database "routing_db\n(PostgreSQL)" as RDB
database "tracking_db\n(PostgreSQL)" as TDB
database "handling_db\n(PostgreSQL)" as HDB
database "billing_db\n(PostgreSQL)" as BIDB

[Web Browser\n(React SPA)] --> [Spring Cloud Gateway\n(@Route + JWT Filter)]
[External System\n(Port Management / Customs)] --> [Spring Cloud Gateway\n(@Route + JWT Filter)]

[Spring Cloud Gateway\n(@Route + JWT Filter)] --> auth_rest
[Spring Cloud Gateway\n(@Route + JWT Filter)] --> booking_rest
[Spring Cloud Gateway\n(@Route + JWT Filter)] --> routing_rest
[Spring Cloud Gateway\n(@Route + JWT Filter)] --> tracking_rest
[Spring Cloud Gateway\n(@Route + JWT Filter)] --> handling_rest
[Spring Cloud Gateway\n(@Route + JWT Filter)] --> billing_rest

auth_repo --> ADB

booking_acl --> routing_rest : REST API（同期）
booking_broker --> MQ : CargoBookedEvent / CargoRoutedEvent /\nCargoCancelledEvent
handling_broker --> MQ : HandlingActivityRegisteredEvent /\nCustomsStatusChangedEvent
MQ --> tracking_sub : イベント購読
MQ --> billing_sub : イベント購読

booking_repo --> BDB
routing_repo --> RDB
tracking_repo --> TDB
handling_repo --> HDB
billing_repo --> BIDB

@enduml
```

## 境界付けられたコンテキスト

### コンテキストマップ

```plantuml
@startuml
title コンテキストマップ（マイクロサービス境界）

package "Booking Context\n(bookingms)" as booking #LightBlue {
  class Cargo <<Aggregate Root>>
  class RouteSpecification <<Value Object>>
  class CargoItinerary <<Value Object>>
  class Delivery <<Value Object>>
  class BookingStatus <<Enum>>
  class CancellationRequest <<Entity>>
}

package "Routing Context\n(routingms)" as routing #LightGreen {
  class Voyage <<Aggregate Root>>
  class CarrierMovement <<Entity>>
  class Schedule <<Value Object>>
  class VoyageNumber <<Value Object>>
}

package "Tracking Context\n(trackingms)" as tracking #LightYellow {
  class TrackingActivity <<Aggregate Root>>
  class TrackingNumber <<Value Object>>
  class TransportStatus <<Enum>>
  class TrackingExceptionEvent <<Entity>>
}

package "Handling Context\n(handlingms)" as handling #LightCoral {
  class HandlingActivity <<Aggregate Root>>
  class HandlingType <<Enum>>
  class CustomsDeclaration <<Aggregate Root>>
  class CustomsStatus <<Enum>>
  class CargoSnapshot <<ACL>>
}

package "Billing Context\n(billingms)" as billing #LightPink {
  class Invoice <<Aggregate Root>>
  class Money <<Value Object>>
  class DiscountPolicy <<Entity>>
  class PaymentStatus <<Enum>>
}

package "Auth Context\n(authms)" as auth #LightSkyBlue {
  class User <<Aggregate Root>>
  class Role <<Entity>>
  class Password <<Value Object>>
  class Email <<Value Object>>
  class AccountLock <<Value Object>>
}

package "Shared Domain\n(共有カーネル)" as shared #WhiteSmoke {
  class Location <<Value Object>>
}

booking --> shared : uses Location
routing --> shared : uses Location
tracking --> shared : uses Location
handling --> shared : uses Location

auth <.. booking : JWT 検証（API Gateway 経由）
auth <.. tracking : JWT 検証（API Gateway 経由）
booking ..> routing : REST API（同期）\nroutes cargo (Conformist)
handling ..> booking : via CargoSnapshot (ACL)
tracking <.. booking : CargoBookedEvent / CargoRoutedEvent /\nCargoCancelledEvent (RabbitMQ 非同期)
tracking <.. handling : HandlingActivityRegisteredEvent /\nCustomsStatusChangedEvent (RabbitMQ 非同期)
billing <.. tracking : CargoDeliveredEvent\n(RabbitMQ 非同期)

note top of handling
  CargoSnapshot は ACL（腐敗防止層）
  Booking → Handling の参照を
  Handling 独自モデルに変換する
  CustomsDeclaration は引取（CLAIM）荷役の
  前提条件（通関済でなければ拒否）
end note

note right of shared
  Location（UN/LOCODE）のみ
  共有カーネルとして維持
  VoyageNumber は各コンテキスト
  固有型として定義
end note

note top of auth
  認証コンテキストは独立した
  マイクロサービスとして分離
  JWT トークンの発行・検証を担当
  AccountLock で総当たり攻撃を防御（US31）
end note

note bottom of booking
  マイクロサービス間通信:
  同期 = REST API
  非同期 = RabbitMQ + Spring Cloud Stream
  輸送中キャンセルは CancellationRequest で
  追跡管理者の承認を経て確定（UC22）
end note

@enduml
```

### 各コンテキストの説明

#### 1. Auth Context（認証コンテキスト）― authms

ユーザー認証・認可の中核ロジックを担う。JWT トークンの発行と検証を責務とする。ビジネスドメインとは独立した支援コンテキスト。
認証失敗 5 回連続でアカウントを一時ロックし、ロック中と認証情報誤りで同一メッセージを返す（US31）。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `User` |
| 主要概念 | `Role`, `Password`, `Email`, `UserName`, `AccountLock`（失敗回数・ロック期限） |
| アクター | 全ユーザー（認証時） |
| DB | `auth_db` |

#### 2. Booking Context（予約コンテキスト）― bookingms

貨物予約の中核ロジックを担う。貨物の登録・経路割り当て・状態管理・キャンセル承認フローを責務とする。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `Cargo` |
| 主要概念 | `RouteSpecification`, `CargoItinerary`, `Delivery`, `CancellationRequest` |
| `BookingStatus` | `PRELIMINARY` / `ROUTE_PROPOSED` / `CONFIRMED` / `TRACKING_ISSUED` / `IN_TRANSIT` / `DELIVERED` / `SETTLED` / `CANCELLED` |
| キャンセル規則 | 輸送開始前は即時キャンセル。`IN_TRANSIT` では `CancellationRequest`（理由必須）を起票し、追跡管理者が陸揚げ地を指定して承認・却下する（UC22） |
| アクター | 荷主、営業担当者、追跡管理者（キャンセル承認） |
| DB | `booking_db` |

#### 3. Routing Context（経路コンテキスト）― routingms

航路・運航スケジュールを管理する。経路候補の算出と最適経路の提案を担う。
誤配時の再設計（US28）では、貨物の現在地を出発地とした経路候補算出を同一 API で提供する。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `Voyage` |
| 主要概念 | `CarrierMovement`, `Schedule`, `VoyageNumber` |
| アクター | 経路設計者 |
| DB | `routing_db` |

#### 4. Tracking Context（追跡コンテキスト）― trackingms

貨物の現在状態・輸送ステータスを管理する。CQRS の読み取り側最適化が特に有効なコンテキスト。
荷役イベントの作業場所が予定ルート外の場合、例外種別「誤配」を自動起票し `MISROUTED` に遷移させる（US28）。
通関の「留置」は例外種別「税関保留」として起票する（UC21 連携）。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `TrackingActivity` |
| 主要概念 | `TrackingNumber`, `TransportStatus`, `TrackingExceptionEvent` |
| `TransportStatus` | `NOT_RECEIVED` / `RECEIVED` / `LOADED` / `IN_TRANSIT` / `UNLOADED` / `AWAITING_CLAIM` / `DELIVERED` / `MISROUTED` |
| 例外種別 | `DELAY`（遅延）/ `DAMAGE`（破損）/ `LOST`（紛失）/ `MISROUTE`（誤配）/ `CUSTOMS_HOLD`（税関保留） |
| アクター | 追跡管理者、荷主、荷受人 |
| DB | `tracking_db` |

#### 5. Handling Context（荷役コンテキスト）― handlingms

港湾での荷役作業と通関申告を記録する。`CargoSnapshot` ACL で Booking Context への依存を吸収する。
通関状態が `CLEARED` でない貨物に対する引取（CLAIM）荷役は拒否する（UC21）。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `HandlingActivity`, `CustomsDeclaration` |
| 主要概念 | `HandlingType`（RECEIVE / LOAD / UNLOAD / CLAIM）, `CustomsStatus`（PENDING / CLEARED / HELD / REJECTED）, `CargoSnapshot`（ACL） |
| 通関規則 | 状態更新には理由の入力が必須で、変更履歴（日時・変更者・理由）を監査ログに残す。HELD が 3 日を超えたら督促通知 |
| アクター | 荷役作業員、追跡管理者（通関状態管理） |
| DB | `handling_db` |

#### 6. Billing Context（請求コンテキスト）― billingms

運賃・請求書の管理を担う。`Money` 値オブジェクトで金額を厳密に管理する。
キャンセル料（状態別料率）と例外に伴う料金調整も本コンテキストで扱う。

| 要素 | 内容 |
| :--- | :--- |
| 集約ルート | `Invoice` |
| 主要概念 | `Money`, `DiscountPolicy`（法人割引 0〜30%）, `PaymentStatus`, `CancellationFee` |
| アクター | 経理担当者、荷主、決済機関 |
| DB | `billing_db` |

#### 7. Shared Domain（共有ドメイン）

`Location`（UN/LOCODE）のみ共有カーネルとして維持する。`VoyageNumber` は各コンテキスト固有型として定義し、共有しない。各マイクロサービスが共有ライブラリとして参照する。

## ヘキサゴナルアーキテクチャ（ポートとアダプター）

```plantuml
@startuml
title ヘキサゴナルアーキテクチャ - Booking Context (bookingms) の例

rectangle "Interfaces（入力側）" as iface #LightBlue {
  [CargoBookingController\n(interfaces/rest/)]
  [CargoBookedEventHandler\n(interfaces/events/)]
}

hexagon "Application Core" as core {
  rectangle "Application Layer\n(application/internal/)" {
    [CargoBookingCommandService\n(commandservices/)]
    [CargoBookingQueryService\n(queryservices/)]
    [ExternalCargoRoutingService\n(outboundservices/acl/)]
  }
  rectangle "Domain Layer\n(domain/model/)" {
    [Cargo\n(aggregates/)]
    [BookCargoCommand\n(commands/)]
    [RouteSpecification\n(valueobjects/)]
  }
  rectangle "Port（インターフェース）" {
    interface "CargoRepository\n(出力ポート)" as repo_port
    interface "ExternalRoutingService\n(出力ポート)" as routing_port
    interface "CargoEventPublisher\n(出力ポート)" as event_port
  }
}

rectangle "Infrastructure（出力側）" as infra #LightGreen {
  [MyBatisCargoRepository\n(infrastructure/repositories/)]
  [RoutingServiceClient\n(infrastructure/services/)]
  [RabbitMQCargoEventPublisher\n(infrastructure/brokers/)]
}

[CargoBookingController\n(interfaces/rest/)] --> [CargoBookingCommandService\n(commandservices/)]
[CargoBookingController\n(interfaces/rest/)] --> [CargoBookingQueryService\n(queryservices/)]
[CargoBookedEventHandler\n(interfaces/events/)] --> [CargoBookingCommandService\n(commandservices/)]

[CargoBookingCommandService\n(commandservices/)] --> [Cargo\n(aggregates/)]
[CargoBookingCommandService\n(commandservices/)] --> repo_port
[CargoBookingCommandService\n(commandservices/)] --> event_port
[ExternalCargoRoutingService\n(outboundservices/acl/)] --> routing_port
[CargoBookingQueryService\n(queryservices/)] --> repo_port

repo_port <|.. [MyBatisCargoRepository\n(infrastructure/repositories/)]
routing_port <|.. [RoutingServiceClient\n(infrastructure/services/)]
event_port <|.. [RabbitMQCargoEventPublisher\n(infrastructure/brokers/)]

@enduml
```

### レイヤー責務一覧

> Practical DDD in Enterprise Java (Chapter 3) のパッケージ構造に準拠する。

| レイヤー | パッケージ | 責務 | 依存方向 |
| :--- | :--- | :--- | :--- |
| **Domain** | `domain/model/aggregates/`, `domain/model/valueobjects/`, `domain/model/commands/`, `domain/model/entities/` | ビジネスルール・不変条件・集約・値オブジェクト・コマンド定義 | 外部に依存しない |
| **Application** | `application/internal/commandservices/`, `application/internal/queryservices/`, `application/internal/outboundservices/acl/` | ユースケース実行・集約操作・ACL 経由の外部マイクロサービス連携 | Domain のみ依存 |
| **Infrastructure** | `infrastructure/repositories/`, `infrastructure/services/`, `infrastructure/brokers/` | 永続化（MyBatis）・外部サービスクライアント・メッセージブローカー | Application / Domain に依存 |
| **Interfaces** | `interfaces/rest/`, `interfaces/rest/dto/`, `interfaces/rest/transform/`, `interfaces/events/` | REST API Controller・DTO・DTO 変換・イベントハンドラ | Application に依存 |

### パッケージ構成（全マイクロサービス）

各バウンデッドコンテキストは独立した Spring Boot アプリケーション（独立した Gradle サブプロジェクト）として構成する。
認証コンテキスト（authms）もビジネスコンテキストと同様に独立したマイクロサービスとする。

> **注（IT1 時点の実装との差）**: 以下の構成は take-3 から引き継いだ目標形であり、IT1 の実装は
> より簡素な粒度になっている。実装は `domain/model` 直下に集約と値オブジェクトを置き、
> 出力ポートを `application/port` に明示して依存性逆転を示す形（`outboundservices/acl` ではない）。
> `aggregates` / `entities` / `valueobjects` の細分は、集約が 1 つの段階では過剰なため
> **必要になった時点で分ける**。各パッケージの実際の責務は `package-info.java` に書いており、
> JIG の出力（用語集・パッケージ図）で確認できる。どちらを正典とするかは IT2 冒頭で決める
> （[IT1 レビュー M5](../review/イテレーション1_review_20260819.md)）。

```
apps/backend/                            Gradle マルチプロジェクトルート
│
├── authms/                              ★ 認証マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/authms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（User, UserId）
│       │       ├── entities/            エンティティ（Role）
│       │       └── valueobjects/        値オブジェクト（Password, Email, UserName, AccountLock）
│       ├── application/
│       │   └── internal/
│       │       ├── commandservices/     コマンドサービス（AuthCommandService）
│       │       └── queryservices/       クエリサービス（AuthQueryService）
│       ├── infrastructure/
│       │   ├── repositories/            リポジトリ実装（MyBatisUserRepository, UserMapper）
│       │   ├── security/               JWT 発行・検証（JwtTokenProvider）
│       │   └── config/                 SecurityConfig, CorsConfig
│       └── interfaces/
│           └── rest/                    REST Controller（AuthController）
│               └── dto/                 LoginRequest, TokenResponse
│
├── bookingms/                           ★ 予約マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/bookingms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（Cargo, BookingId）
│       │       ├── commands/            コマンド（BookCargoCommand, RouteCargoCommand,
│       │       │                          RequestCancellationCommand, ApproveCancellationCommand）
│       │       ├── entities/            エンティティ（Location, CancellationRequest）
│       │       └── valueobjects/        値オブジェクト（RouteSpecification, Delivery, Leg 等）
│       ├── application/
│       │   └── internal/
│       │       ├── commandservices/     コマンドサービス（CargoBookingCommandService）
│       │       ├── queryservices/       クエリサービス（CargoBookingQueryService）
│       │       └── outboundservices/
│       │           └── acl/             ACL（ExternalCargoRoutingService）
│       ├── infrastructure/
│       │   ├── repositories/            リポジトリ実装（MyBatisCargoRepository, CargoMapper）
│       │   ├── services/                外部サービス実装（RoutingServiceClient）
│       │   └── brokers/
│       │       └── rabbitmq/            RabbitMQ イベント発行（CargoEventSource）
│       ├── interfaces/
│       │   ├── rest/                    REST Controller（CargoBookingController）
│       │   │   ├── dto/                 リクエスト / レスポンス DTO
│       │   │   └── transform/           DTO ⇔ コマンド変換（Assembler）
│       │   └── events/                  イベントハンドラ（CargoBookedEventHandler）
│       └── shareddomain/                共有ドメイン（Location, クロスコンテキストイベント）
│           ├── model/
│           └── events/
│
├── routingms/                           ★ 経路設計マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/routingms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（Voyage, VoyageNumber）
│       │       ├── entities/            エンティティ（CarrierMovement）
│       │       └── valueobjects/        値オブジェクト（Schedule, TransitPath, TransitEdge）
│       ├── application/
│       │   └── internal/
│       │       ├── commandservices/     コマンドサービス（VoyageCommandService）
│       │       └── queryservices/       クエリサービス（CargoRoutingQueryService）
│       ├── infrastructure/
│       │   └── repositories/            リポジトリ実装（MyBatisVoyageRepository, VoyageMapper）
│       └── interfaces/
│           └── rest/                    REST Controller（CargoRoutingController）
│               ├── dto/
│               └── transform/
│
├── trackingms/                          ★ 追跡マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/trackingms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（TrackingActivity, TrackingNumber）
│       │       ├── entities/            エンティティ（TrackingExceptionEvent）
│       │       └── valueobjects/        値オブジェクト（TransportStatus, ExceptionType）
│       ├── application/
│       │   └── internal/
│       │       ├── commandservices/     コマンドサービス（TrackingCommandService）
│       │       └── queryservices/       クエリサービス（TrackingQueryService）
│       ├── infrastructure/
│       │   └── repositories/            リポジトリ実装（MyBatisTrackingRepository）
│       └── interfaces/
│           ├── rest/                    REST Controller（TrackingController）
│           └── events/                  イベント受信（CargoRoutedEventHandler,
│                                          CustomsStatusChangedEventHandler）
│
├── handlingms/                          ★ 荷役マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/handlingms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（HandlingActivity, CustomsDeclaration）
│       │       ├── entities/            エンティティ（CargoSnapshot ― ACL, CustomsStatusHistory）
│       │       └── valueobjects/        値オブジェクト（HandlingType, CustomsStatus）
│       ├── application/
│       │   └── internal/
│       │       └── commandservices/     コマンドサービス（HandlingCommandService,
│       │                                  CustomsCommandService）
│       ├── infrastructure/
│       │   ├── repositories/            リポジトリ実装（MyBatisHandlingRepository）
│       │   └── brokers/
│       │       └── rabbitmq/            RabbitMQ イベント発行
│       └── interfaces/
│           └── rest/                    REST Controller（HandlingController, CustomsController）
│
├── billingms/                           ★ 請求マイクロサービス（独立デプロイ）
│   └── src/main/java/com/example/billingms/
│       ├── domain/
│       │   └── model/
│       │       ├── aggregates/          集約ルート（Invoice）
│       │       ├── entities/            エンティティ（DiscountPolicy）
│       │       └── valueobjects/        値オブジェクト（Money, PaymentStatus, CancellationFee）
│       ├── application/
│       │   └── internal/
│       │       ├── commandservices/     コマンドサービス（BillingCommandService）
│       │       └── queryservices/       クエリサービス（BillingQueryService）
│       ├── infrastructure/
│       │   └── repositories/            リポジトリ実装（MyBatisInvoiceRepository）
│       └── interfaces/
│           ├── rest/                    REST Controller（BillingController）
│           └── events/                  イベント受信（CargoDeliveredEventHandler）
│
├── gatewayms/                           ★ API Gateway（独立デプロイ）
│   └── src/main/java/com/example/gatewayms/
│       └── config/                      ルーティング定義、JWT フィルター
│
├── shared/                              ★ 共有ライブラリ（デプロイ単位ではない）
│   └── src/main/java/com/example/shared/
│       └── domain/
│           └── model/                   Location（UN/LOCODE）等
│
├── settings.gradle                      Gradle マルチプロジェクト設定
├── build.gradle                         共通設定（Java, 品質管理, テスト）
├── gradlew, gradlew.bat                 Gradle Wrapper
└── config/
    ├── checkstyle/checkstyle.xml         Checkstyle ルール
    └── spotbugs/exclude-filter.xml       SpotBugs 除外フィルター
```

> **ポイント**: `apps/backend/` が Gradle マルチプロジェクトのルートであり、
> 各ディレクトリ（authms, bookingms, routingms, ...）は独立した Gradle サブプロジェクトとなる。
> それぞれが独自の `build.gradle`、`application.yml`、`Dockerfile` を持つ。
> `shared/` のみライブラリとして各サービスが `implementation project(':shared')` で依存する。
> Kubernetes マニフェスト（`apps/k8s/kustomize/`）とフロントエンド（`apps/frontend/`）は `apps/` 直下に配置する。

## CQRS 設計

```plantuml
@startuml
title CQRS - コマンド・クエリ分離

package "Command Side（書き込み）" as cmd #LightBlue {
  [Command Controller]
  [Command Service\n（ユースケース実行）]
  [Domain Model\n（集約・エンティティ）]
  [MyBatis Mapper\n（書き込み用）]
}

package "Query Side（読み取り）" as qry #LightGreen {
  [Query Controller]
  [Query Service\n（読み取り最適化）]
  [Query DTO\n（フラット構造）]
  [MyBatis Mapper\n（読み取り用 SQL）]
}

database "PostgreSQL\n(サービス専用 DB)" as db

[Command Controller] --> [Command Service\n（ユースケース実行）]
[Command Service\n（ユースケース実行）] --> [Domain Model\n（集約・エンティティ）]
[Domain Model\n（集約・エンティティ）] --> [MyBatis Mapper\n（書き込み用）]
[MyBatis Mapper\n（書き込み用）] --> db

[Query Controller] --> [Query Service\n（読み取り最適化）]
[Query Service\n（読み取り最適化）] --> [MyBatis Mapper\n（読み取り用 SQL）]
[MyBatis Mapper\n（読み取り用 SQL）] --> db
[MyBatis Mapper\n（読み取り用 SQL）] --> [Query DTO\n（フラット構造）]

note right of [Query DTO\n（フラット構造）]
  JOIN を含む複雑な SQL で
  画面表示に最適化した DTO を
  直接 MyBatis でマッピングする
  ドメインモデルを経由しない
end note

@enduml
```

### CQRS 適用方針

- **コマンド側**: ドメインモデル（集約）を通じて状態変更。不変条件の検証後、MyBatis で永続化する
- **クエリ側**: ドメインモデルを経由せず、MyBatis の XML マッパーで JOIN クエリを直接記述し、画面表示用 DTO を返す
- **CQRS が特に有効なコンテキスト**: Booking（一覧・詳細の頻繁な参照）、Tracking（リアルタイム状態確認）

## イベント駆動設計

```plantuml
@startuml
title ドメインイベント - Spring Cloud Stream + RabbitMQ（マイクロサービス間）

participant "Booking\nCommandService\n(bookingms)" as booking
participant "RabbitMQ\nCargoEventPublisher\n(bookingms)" as publisher
participant "RabbitMQ\nBroker" as mq
participant "CargoRoutedEventHandler\n(trackingms)" as tracking_handler
participant "Tracking\nCommandService\n(trackingms)" as tracking

booking -> publisher : publishEvent(CargoRoutedEvent)
publisher -> mq : send to cargoRoutingChannel
mq -> tracking_handler : receive
tracking_handler -> tracking : updateTransportStatus(event)

note over mq
  マイクロサービス間は RabbitMQ で
  非同期通信（Spring Cloud Stream）
  サービス間の疎結合を実現
end note

@enduml
```

### ドメインイベント一覧

| イベント | 発行元サービス | 処理先サービス | チャネル | 内容 |
| :--- | :--- | :--- | :--- | :--- |
| `CargoBookedEvent` | bookingms | trackingms | cargoBookingChannel | 追跡番号の割り当てトリガー |
| `CargoRoutedEvent` | bookingms | trackingms | cargoRoutingChannel | 経路・旅程の確定を追跡に通知 |
| `CargoCancelledEvent` | bookingms | trackingms, billingms | cargoCancellationChannel | キャンセル確定 → 追跡終了・キャンセル料算定 |
| `HandlingActivityRegisteredEvent` | handlingms | trackingms, bookingms | handlingChannel | 荷役作業登録 → 輸送ステータス同期。予定ルート外の作業場所は誤配検知の入力（US28） |
| `CustomsStatusChangedEvent` | handlingms | trackingms | customsChannel | 通関状態変更 → HELD なら例外「税関保留」を自動起票、CLEARED なら通関完了通知（UC21） |
| `CargoDeliveredEvent` | trackingms | billingms | deliveryChannel | 配送完了 → 精算開始 |
| `InvoiceCreatedEvent` | billingms | （通知システム） | billingChannel | 請求書発行 → 荷主への通知 |

### Spring Cloud Stream + RabbitMQ の実装方針

```java
// イベント発行（bookingms - infrastructure/brokers/rabbitmq/）
@Service
public class RabbitMQCargoEventPublisher {
    private final StreamBridge streamBridge;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCargoBookedEvent(CargoBookedEvent event) {
        streamBridge.send("cargoBooking-out-0", event);
    }
}

// イベント受信（trackingms - interfaces/events/）
@Configuration
public class CargoRoutedEventHandler {
    private final TrackingCommandService trackingCommandService;

    @Bean
    public Consumer<CargoRoutedEvent> cargoRouting() {
        return trackingCommandService::initializeTracking;
    }
}
```

> **設計注意**: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` を使用し、
> トランザクションコミット後にイベントを発行する。
> 高可用性が必要なシステムへ移行する際は Transactional Outbox パターンへの移行を検討すること。

## マイクロサービス間通信

### 同期通信（REST API）

Booking Service が Routing Service の経路候補を取得する際、ACL を介して REST API を呼び出す。

```java
// ACL（bookingms - application/internal/outboundservices/acl/）
@Service
public class ExternalCargoRoutingService {
    private final RestClient restClient;

    public CargoItinerary fetchRouteForSpecification(RouteSpecification spec) {
        TransitPath transitPath = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/routes/optimal")
                .queryParam("origin", spec.getOrigin().getUnLocCode())
                .queryParam("destination", spec.getDestination().getUnLocCode())
                .queryParam("deadline", spec.getArrivalDeadline())
                .build())
            .retrieve()
            .body(TransitPath.class);

        return toCargoItinerary(transitPath);
    }

    private CargoItinerary toCargoItinerary(TransitPath transitPath) {
        List<Leg> legs = transitPath.getTransitEdges().stream()
            .map(edge -> new Leg(
                edge.getVoyageNumber(),
                edge.getFromUnLocode(),
                edge.getToUnLocode(),
                edge.getFromDate(),
                edge.getToDate()))
            .toList();
        return new CargoItinerary(legs);
    }
}
```

### 通信方式一覧

| 通信パターン | 発信元 | 発信先 | 方式 | エンドポイント / チャネル |
| :--- | :--- | :--- | :--- | :--- |
| 同期 | bookingms | routingms | REST | `GET /api/v1/routes/optimal` |
| 非同期 | bookingms | trackingms | RabbitMQ | `cargoBookingChannel` |
| 非同期 | bookingms | trackingms | RabbitMQ | `cargoRoutingChannel` |
| 非同期 | bookingms | trackingms, billingms | RabbitMQ | `cargoCancellationChannel` |
| 非同期 | handlingms | trackingms, bookingms | RabbitMQ | `handlingChannel` |
| 非同期 | handlingms | trackingms | RabbitMQ | `customsChannel` |
| 非同期 | trackingms | billingms | RabbitMQ | `deliveryChannel` |

## データベース設計方針

### Database per Service パターン

各マイクロサービスが専用のデータベースを持つ。他サービスのデータには直接アクセスしない。

| サービス | データベース名 | 主要テーブル | RDBMS |
| :--- | :--- | :--- | :--- |
| authms | auth_db | users, roles, user_roles | PostgreSQL 16.x |
| bookingms | booking_db | cargo, leg, shipper, cancellation_request | PostgreSQL 16.x |
| routingms | routing_db | voyage, carrier_movement | PostgreSQL 16.x |
| trackingms | tracking_db | tracking_activity, handling_event, tracking_exception_event | PostgreSQL 16.x |
| handlingms | handling_db | handling_activity, customs_declaration, customs_status_history | PostgreSQL 16.x |
| billingms | billing_db | invoice, discount_policy | PostgreSQL 16.x |

### MyBatis 実装例

```java
// ドメイン層: リポジトリインターフェース（出力ポート）
public interface CargoRepository {
    void save(Cargo cargo);
    Optional<Cargo> findByBookingId(BookingId bookingId);
    List<Cargo> findAll();
}

// インフラ層: MyBatis Mapper
@Mapper
public interface CargoMapper {
    void insertCargo(CargoRecord record);
    void updateCargo(CargoRecord record);
    CargoRecord selectByBookingId(@Param("bookingId") String bookingId);
    List<CargoRecord> selectAll();
}

// インフラ層: リポジトリ実装
@Repository
public class MyBatisCargoRepository implements CargoRepository {
    private final CargoMapper cargoMapper;

    @Override
    public void save(Cargo cargo) {
        CargoRecord record = CargoRecordAssembler.toRecord(cargo);
        if (cargo.getId() == null) {
            cargoMapper.insertCargo(record);
        } else {
            cargoMapper.updateCargo(record);
        }
    }

    @Override
    public Optional<Cargo> findByBookingId(BookingId bookingId) {
        CargoRecord record = cargoMapper.selectByBookingId(bookingId.getBookingId());
        return Optional.ofNullable(record)
            .map(CargoRecordAssembler::toDomainModel);
    }
}
```

### トランザクション管理

- **サービス内**: Spring の `@Transactional` で管理
- **サービス間**: 結果整合性（Eventual Consistency）をイベント駆動で実現
- **補償トランザクション**: 失敗時はドメインイベントで補償処理を実行

## API 設計方針

### REST API 設計原則

| 原則 | 内容 |
| :--- | :--- |
| **リソース指向** | URL はリソースを表す名詞。動詞は HTTP メソッドで表現する |
| **バージョニング** | `/api/v1/` プレフィックスでバージョンを管理する |
| **レスポンス形式** | JSON。エラーレスポンスは `{ "code": "BOOKING_NOT_FOUND", "message": "..." }` 形式 |
| **ステータスコード** | 成功: 200/201/204、クライアントエラー: 400/403/404/409、サーバーエラー: 500 |
| **API Gateway** | Spring Cloud Gateway で各マイクロサービスへルーティングする |

### 主要エンドポイント

#### authms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | ログイン（JWT 発行）。失敗 5 回連続でロック | UC20 |
| `POST` | `/api/v1/auth/logout` | ログアウト（セッション破棄記録） | UC20 |
| `GET` | `/api/v1/auth/me` | 認証ユーザー情報取得 | UC20 |

##### `POST /api/v1/auth/login` の契約

IT1 でログイン画面のニーズから導出した（アウトサイドイン）。

リクエスト:

```json
{ "userId": "sales01", "password": "..." }
```

成功（200）:

```json
{
  "token": "<JWT>",
  "userId": "sales01",
  "displayName": "山田太郎",
  "roles": ["ROLE_SALES"]
}
```

失敗（401）:

```json
{ "message": "利用者 ID またはパスワードが正しくありません" }
```

- **認証情報誤り・アカウントロック中・無効化アカウントはすべて 401 かつ同一の文言で返す**（US31）。
  理由を区別すると「その利用者 ID は存在する」ことを攻撃者に教えてしまう。ロック発生の通知は
  メール等の帯域外で行う
- `displayName` と `roles` を応答に含めるのは、画面が誰として入っているか・どのメニューを出すかを
  判断するため。トークンを復号して得るのではなく明示的に返す（画面が JWT の中身に依存しない）

#### bookingms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/shippers` | 荷主の登録（個人・法人） | UC02 |
| `GET` | `/api/v1/shippers` | 荷主一覧・検索 | UC02 |
| `GET` | `/api/v1/shippers/{shipperId}` | 荷主詳細の取得 | UC02 |

##### `POST /api/v1/shippers` の契約（メールアドレス重複時の分岐）

IT1 で荷主登録画面のニーズから導出した。

リクエスト:

```json
{
  "type": "INDIVIDUAL",
  "name": "山田太郎",
  "email": "yamada@example.com",
  "address": "東京都千代田区 1-1-1",
  "phone": "03-1234-5678",
  "registerAnyway": false
}
```

登録成功（201）: 採番された荷主を返す（`shipperCode` は `SHP-` + 6 桁）。

同一メールアドレスの荷主が既にある（409）:

```json
{
  "message": "同じメールアドレスの荷主が既に登録されています",
  "existing": { "id": 1, "shipperCode": "SHP-000001", "type": "INDIVIDUAL", "name": "山田太郎", "address": "...", "phone": "..." }
}
```

- **409 は失敗ではなく利用者への問いかけである。** 営業担当者は既存の荷主を使うか、別の荷主として登録するかを、その場の事情で判断する（同姓同名の別のお客様、同じ代表アドレスを使う別部署が実在する）。したがって `shipper.email` は UNIQUE にしない
- `registerAnyway: true` を送ると重複を確認せず新しい荷主として登録し、別の荷主コードを採番する
- 同一メールが複数ある場合、`existing` には**最初に登録された荷主**を返す。毎回違う荷主を提示すると営業の判断が揺れる
- 画面側は 409 を受けて「既存の荷主を使う」「それでも新規で登録する」の 2 択を出す（`ui_design.md` の荷主登録）
| `POST` | `/api/v1/bookings` | 貨物予約の登録 | UC03 |
| `GET` | `/api/v1/bookings/{bookingId}` | 予約詳細の取得 | UC03 |
| `GET` | `/api/v1/bookings` | 予約一覧の取得 | UC03 |
| `PUT` | `/api/v1/bookings/{bookingId}/route` | 経路の割り当て（誤配再設計時は現在地起点） | UC09, UC08 |
| `PUT` | `/api/v1/bookings/{bookingId}/confirm` | 予約確定 | UC11 |
| `POST` | `/api/v1/bookings/{bookingId}/tracking-number` | 追跡番号発行 | UC12 |
| `POST` | `/api/v1/bookings/{bookingId}/cancellation` | キャンセル申請（輸送開始前は即確定、輸送中は承認待ち） | UC22 |
| `PUT` | `/api/v1/bookings/{bookingId}/cancellation/approve` | キャンセル承認（追跡管理者・陸揚げ地指定） | UC22 |
| `PUT` | `/api/v1/bookings/{bookingId}/cancellation/reject` | キャンセル却下（理由必須） | UC22 |

#### routingms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/voyages` | 航海スケジュール一覧 | UC05 |
| `POST` | `/api/v1/voyages` | 航海スケジュール登録 | UC19 |
| `PUT` | `/api/v1/voyages/{voyageNumber}` | 航海スケジュール更新 | UC19 |
| `GET` | `/api/v1/routes/optimal` | 最適経路候補算出（origin に現在地を指定して再設計可） | UC06 |

#### trackingms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/public/tracking/{trackingNumber}` | 追跡情報照会（**認証不要**。公開経路は `/api/v1/public/` 配下に分ける） | UC15 |
| `PUT` | `/api/v1/tracking/{trackingNumber}/status` | 貨物状態更新 | UC14 |
| `POST` | `/api/v1/tracking/{trackingNumber}/exceptions` | 例外処理（遅延・破損・紛失・誤配・税関保留） | UC16 |
| `PUT` | `/api/v1/tracking/{trackingNumber}/exceptions/{exceptionId}/resolve` | 例外解決の記録 | UC16 |

#### handlingms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/handling` | 荷役作業の登録（CLAIM は通関済チェック） | UC13 |
| `POST` | `/api/v1/customs` | 通関申告の登録（初期状態 PENDING） | UC21 |
| `PUT` | `/api/v1/customs/{declarationId}/status` | 通関状態の更新（理由必須・監査ログ） | UC21 |
| `GET` | `/api/v1/customs` | 通関申告一覧（貨物 ID・追跡番号・状態で検索） | UC21 |

#### billingms

| メソッド | パス | 説明 | 対応 UC |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/billing/{bookingId}/calculate` | 輸送料金算出（法人割引・キャンセル料・例外調整含む） | UC17 |
| `POST` | `/api/v1/billing/{bookingId}/settlement` | 精算処理 | UC18 |

## セキュリティ設計

### Spring Security による認証・認可

```plantuml
@startuml
title Spring Security - 認証・認可フロー（API Gateway 統合）

actor User
participant "API Gateway\n(Spring Cloud Gateway)" as gw
participant "Auth Service\n(JWT 発行)" as auth
participant "Microservice\n(bookingms 等)" as ms
database "PostgreSQL\n(users テーブル)" as db

User -> gw : HTTP Request + JWT
gw -> gw : JWT 検証
gw -> ms : 認証済みリクエスト転送

note over gw
  JWT の検証は API Gateway で実施
  各マイクロサービスは JWT のクレームから
  ロール情報を取得して認可チェック
  追跡照会 (GET /api/v1/public/tracking/*) のみ認証不要
end note

User -> auth : ログイン（ID/PW）
auth -> db : ユーザー情報取得
db --> auth : UserDetails
auth --> User : JWT トークン

@enduml
```

### ロール設計

| ロール | 権限 | 対象ユーザー |
| :--- | :--- | :--- |
| `ROLE_SHIPPER` | 予約照会・追跡照会・キャンセル申し出 | 荷主 |
| `ROLE_SALES` | 荷主登録・予約登録・見積・キャンセル申請 | 営業担当者 |
| `ROLE_ROUTING` | 航海スケジュール管理・経路候補算出・経路確定・追跡番号発行 | 経路設計者 |
| `ROLE_HANDLER` | 荷役作業登録・通関申告登録 | 荷役作業員 |
| `ROLE_TRACKER` | 追跡情報管理・例外対応・通関状態管理・キャンセル承認 | 追跡管理者 |
| `ROLE_ACCOUNTANT` | 請求書管理 | 経理担当者 |
| `ROLE_ADMIN` | 全機能・アカウントロック解除 | システム管理者 |

## テスト戦略

```plantuml
@startuml
title テストピラミッド

package "E2E テスト（少量）" #LightCoral {
  [Playwright\n主要ユーザーシナリオ] as e2e
}

package "統合テスト（中程度）" #LightYellow {
  [Testcontainers（PostgreSQL, RabbitMQ）\nMyBatis マッパー / Spring MockMvc\nContract テスト（サービス間）] as integration
}

package "単体テスト（多数）" #LightGreen {
  [JUnit 5 + Mockito + AssertJ\nドメインモデル・サービス] as unit
}

@enduml
```

### 各層のテスト方針

| テスト対象 | テスト種別 | 使用技術 | 方針 |
| :--- | :--- | :--- | :--- |
| ドメインモデル（集約・値オブジェクト） | 単体テスト | JUnit 5, AssertJ | 依存なし。ビジネスルール（通関ガード・キャンセル承認・誤配検知）を網羅的にテスト |
| Application Service | 単体テスト | JUnit 5, Mockito | リポジトリをモック化。ユースケースのフローをテスト |
| MyBatis Mapper | 統合テスト | Testcontainers（PostgreSQL） | 実 DB への SQL を検証。スキーマを Flyway で適用 |
| REST Controller | 統合テスト | Spring MockMvc | エンドポイントの入出力・バリデーション・認可をテスト |
| サービス間契約 | Contract テスト | Spring Cloud Contract | マイクロサービス間 API の契約を検証 |
| アーキテクチャ | アーキテクチャテスト | ArchUnit | レイヤー依存・BC 独立性（ACL ポートのみ越境可）を検証 |
| E2E | E2E テスト | Playwright | 主要ユーザーシナリオ（予約 → 経路設計 → 追跡 → 通関 → 引取 → 精算）を検証 |

## マイクロサービス技術スタック

| カテゴリ | 技術 | バージョン |
| :--- | :--- | :--- |
| フレームワーク | Spring Boot | 4.x |
| Java | Eclipse Temurin | 25 |
| データアクセス | MyBatis + MyBatis Spring Boot Starter | 4.x |
| メッセージング | Spring Cloud Stream + RabbitMQ | 4.x |
| API ゲートウェイ | Spring Cloud Gateway | 4.x |
| サービス間通信 | RestClient / WebClient | - |
| データベース | PostgreSQL / H2（開発用） | 16.x / 2.x |
| マイグレーション | Flyway | 10.x |
| API ドキュメント | springdoc-openapi | 3.x |
| ビルドツール | Gradle (Wrapper) | 9.x |
| 品質管理 | Checkstyle / SpotBugs | - |
| カバレッジ | JaCoCo | - |
| 品質分析 | SonarQube | - |
| コンテナ | Docker / Kubernetes（kind）+ Kustomize | - |
| テスト | JUnit 5, Mockito, AssertJ, Testcontainers | - |
| アーキテクチャテスト | ArchUnit | 1.x |
| Contract テスト | Spring Cloud Contract | 4.x |

## 参照

- [要件定義書](../requirements/requirements_definition.md)
- [システムユースケース](../requirements/system_usecase.md)
- [ユーザーストーリー](../requirements/user_story.md)
- [フロントエンドアーキテクチャ設計](architecture_frontend.md)
- [インフラストラクチャアーキテクチャ設計](architecture_infrastructure.md)
- [アーキテクチャ設計ガイド](../reference/アーキテクチャ設計ガイド.md)
