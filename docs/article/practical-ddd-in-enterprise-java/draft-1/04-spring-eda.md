---
type: Article
title: "第 4 章：Spring Platform × EDA"
description: "Spring プラットフォーム上でイベント駆動アーキテクチャとして Cargo Tracker を構成する（draft-1）。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-27T08:25:38Z }
---

# 第 4 章：Spring Platform × EDA

この章では、Cargo Tracker を EDA（Event-Driven Architecture）として読み解きます。ポイントは「イベントを使っているか」ではなく、**境界を越える通信をイベントと ACL ポートに限定し、各 BC が自分のモデルだけを更新する**設計です。

## Spring プラットフォーム

### Spring Boot: 機能

基盤は Spring Boot です。単一プロセスで起動しつつ、BC ごとにパッケージ分割してモジュラーモノリスを形成します。

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class CargoTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CargoTrackerApplication.class, args);
    }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/CargoTrackerApplication.java`

### Spring Framework のまとめ

EDA の実装で使う主要機能は次の 3 つです。

1. `ApplicationEventPublisher` によるイベント発行
2. `@TransactionalEventListener(AFTER_COMMIT)` による購読
3. `@Service` / `@Component` によるユースケースとアダプタの結線

## EDA としての Cargo Tracker

### 境界づけられたコンテキスト

トップレベルパッケージが BC と 1 対 1 です。BC 間通信はイベントか ACL ポートに限定されます。

```java
/**
 * BC 間の通信はドメインイベントか ACL ポートに限る
 */
package com.example.cargotracker;
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/package-info.java`

#### 境界づけられたコンテキスト：パッケージング

- `booking`
- `shipper`
- `routing`
- `tracking`
- `handling`
- `billing`
- `estimation`
- `shared`

#### 境界づけられたコンテキスト：パッケージ構造

`booking` BC を例にすると、EDA の責務は以下の 4 層に分離されています。

```text
com.example.cargotracker.booking
├─ interfaces
│  ├─ web
│  └─ events
├─ application
│  └─ internal
│     ├─ commandservices
│     ├─ queryservices
│     └─ outboundservices/acl
├─ domain
│  ├─ model
│  └─ repository
└─ infrastructure
   ├─ repositories
   └─ acl
```

参照:

- `.../booking/domain/model/package-info.java`
- `.../booking/application/internal/queryservices/package-info.java`
- `.../booking/interfaces/events/package-info.java`
- `.../booking/infrastructure/acl/package-info.java`
- `.../booking/infrastructure/repositories/package-info.java`

### Cargo Tracker の実装

### ドメインモデル：実装

#### コアドメインモデル：実装

`booking` の集約・値オブジェクトは Spring 依存を持たず、業務規則を自己完結で保持します。

#### ドメインモデルの操作

##### コマンド

更新系はアプリケーションサービスで受け、集約を進めます。BC 越境が必要な確認（荷主存在確認など）は ACL ポート経由です。

```java
@Service
public class BookCargoCommandService {
    @Transactional
    public Result book(BookCargoCommand command, String actor) {
        if (!shipperExistenceChecker.exists(command.shipperId())) {
            return Result.shipperNotFound();
        }
        ...
    }
}
```

参照: `.../booking/application/internal/commandservices/BookCargoCommandService.java`

##### クエリ

参照系は `queryservices` へ分離し、画面に必要なビューを返します。

```java
public interface BookingQueryService {
    Page<BookingView> search(BookingSearchCriteria criteria, PageRequest page);
    Page<BookingView> findAwaitingRouting(PageRequest page);
    Optional<BookingView> findById(String bookingId);
}
```

参照: `.../booking/application/internal/queryservices/BookingQueryService.java`

##### ドメインイベント

イベントは「起きた事実」だけを運ぶ `record` で定義します。

```java
public record CargoStatusUpdatedEvent(
        UUID bookingId,
        String trackingNumber,
        String transportStatusLabel,
        Instant occurredAt,
        String locationUnlocode,
        String updatedBy) {
}
```

参照: `.../shared/domain/event/CargoStatusUpdatedEvent.java`

購読側は `AFTER_COMMIT` で受けて、自 BC の操作へ翻訳します。

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(CargoStatusUpdatedEvent event) {
    var result = recordService.recordNotification(event);
    ...
}
```

参照: `.../booking/interfaces/events/BookingStatusNotificationHandler.java`

### ドメインモデルサービス

ドメインサービス（例: `RouteSearchService`）は業務計算を担当し、イベント駆動の流れでも集約の責務を肥大化させない役割を持ちます。

### 送信サービス

送信側の契約は利用側 BC が定義し、提供側 BC が実装します。

```java
public interface ShipperExistenceChecker {
    boolean exists(ShipperId shipperId);
    Optional<ShipperId> findIdByShipperCode(String shipperCode);
}
```

```java
@Component
public class ShipperExistenceCheckerAdapter implements ShipperExistenceChecker { ... }
```

参照:

- `.../booking/application/internal/outboundservices/acl/ShipperExistenceChecker.java`
- `.../shipper/infrastructure/acl/ShipperExistenceCheckerAdapter.java`

### 実装のまとめ

Cargo Tracker の EDA 実装は、次の 4 点で成立しています。

1. 事実はイベントで通知し、判断は購読側 BC で行う
2. イベントは `AFTER_COMMIT` で購読し、整合性の境界を守る
3. 参照はクエリ、更新はコマンドへ分離する
4. BC 越境の同期連携は ACL ポートで明示する

## まとめ

Spring Platform 上でも、EDA は「イベントを増やすこと」ではなく、境界と責務を固定するための設計手段として機能します。次章では、同じ題材を CQRS/ES（Axon）として再構成し、イベント中心設計をさらに進めます。
