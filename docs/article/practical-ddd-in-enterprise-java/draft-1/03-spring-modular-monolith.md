# 第 3 章：Spring Platform × モジュラーモノリス

この章では、Cargo Tracker の DDD モデルを Spring Platform 上でどう実装しているかを整理します。主眼は「Spring を使う」こと自体ではなく、**ドメイン境界を壊さずに Spring を外側へ配置する方法**です。

## Spring プラットフォーム

### Spring Boot: 機能

起動は標準的な `@SpringBootApplication` です。

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

利用機能は `build.gradle` で明示されています。

- `spring-boot-starter-web`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-flyway`
- `spring-boot-starter-actuator`
- `springdoc-openapi-starter-webmvc-ui`
- MyBatis starter

参照: `docs/article/source/java-2/apps/cargo-tracker/build.gradle`

### Spring Framework のまとめ

本実装での Spring の役割は、次の 3 点に絞られています。

1. DI によるユースケース・リポジトリ・アダプタの結線（`@Service`, `@Repository`, `@Component`）
2. トランザクション境界の制御（`@Transactional`）
3. Web 入出力とイベント購読の外側実装（`@Controller`, `@TransactionalEventListener`）

ドメインオブジェクト自体は Spring アノテーションに依存しません。

## モジュラーモノリスとしての Cargo Tracker

### 境界づけられたコンテキスト

トップレベルパッケージが BC と 1 対 1 で対応します。

```java
/**
 * トップレベルのパッケージは Bounded Context と 1 対 1 である
 */
package com.example.cargotracker;
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/package-info.java`

### ドメインモデルの実装

`domain.model` に集約・値オブジェクト・エンティティを配置し、BC ごとの一貫性境界を維持します。たとえば `booking` の集約ルートは `Cargo` です。

```java
public class Cargo {
    public static Cargo book(BookCargoCommand command) { ... }
    public void confirm(ClaimCode issued) { ... }
}
```

参照: `.../booking/domain/model/aggregates/Cargo.java`

### ドメインモデルサービスの実装

複数オブジェクトにまたがる業務計算はドメインサービスへ分離されます。`routing` では経路探索を `RouteSearchService` が担います。

```java
public class RouteSearchService {
    public List<ProposedRoute> search(RoutingCriteria criteria, List<Voyage> voyages) { ... }
}
```

参照: `.../routing/domain/model/RouteSearchService.java`

### 受信サービス

受信側は `interfaces.web` と `interfaces.events` で構成されます。Web 画面は `@Controller` で受け、イベントは `@TransactionalEventListener` で購読します。

```java
@Controller
@RequestMapping("/bookings")
public class BookingController { ... }
```

参照: `.../booking/interfaces/web/BookingController.java`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(CargoStatusUpdatedEvent event) { ... }
```

参照: `.../booking/interfaces/events/BookingStatusNotificationHandler.java`

### RESTful API

本実装は JSON API 中心ではなく、サーバサイド HTML を返す構成です。ただし URL 設計と HTTP メソッドは RESTful なリソース操作に沿っています。

```java
@Controller
@RequestMapping("/bookings/{bookingId}/route")
public class RouteAssignmentController {
    @GetMapping public String show(...) { ... }
    @PostMapping("/proposals") public String propose(...) { ... }
    @PostMapping("/selection") public String select(...) { ... }
}
```

参照: `.../routing/interfaces/web/RouteAssignmentController.java`

### ネイティブ Web API

Thymeleaf + htmx により、HTML フラグメントを API 的に利用します。`BookingController` の種別入力欄差し替えはその代表です。

```java
@GetMapping("/new/specification")
public String specificationFields(...) {
    return "booking/_specification :: fields";
}
```

参照: `.../booking/interfaces/web/BookingController.java`

また、各 BC の `interfaces.web/package-info.java` でも Thymeleaf + htmx 方針が明示されています。

### アプリケーションサービス

ユースケースは `application.internal.commandservices` に集約され、集約操作と境界外確認を仲介します。

```java
@Service
public class BookCargoCommandService {
    @Transactional
    public Result book(BookCargoCommand command, String actor) { ... }
}
```

参照: `.../booking/application/internal/commandservices/BookCargoCommandService.java`

### アプリケーションサービス：イベント

状態変化の通知は `ApplicationEventPublisher` で発行し、購読側は `AFTER_COMMIT` で反映します。これにより、発行元ロールバック時の不整合を抑えます。

```java
events.publishEvent(new CargoCancelledEvent(...));
```

参照: `.../booking/application/internal/commandservices/CancelBookingApprovalCommandService.java`

### 送信サービス

BC 間連携は `application.internal.outboundservices.acl` に定義したポート経由です。

```java
public interface ShipperExistenceChecker {
    boolean exists(ShipperId shipperId);
    Optional<ShipperId> findIdByShipperCode(String shipperCode);
}
```

参照: `.../booking/application/internal/outboundservices/acl/ShipperExistenceChecker.java`

実装は提供側 BC（`shipper`）に置き、依存方向を一方向へ保ちます。

```java
@Component
public class ShipperExistenceCheckerAdapter implements ShipperExistenceChecker { ... }
```

参照: `.../shipper/infrastructure/acl/ShipperExistenceCheckerAdapter.java`

### 実装のまとめ

モジュラーモノリスとしての実装上のポイントは次のとおりです。

1. BC = パッケージ境界を先に固定する
2. Spring は外側（interfaces / infrastructure / application）に寄せる
3. 越境は ACL ポートとドメインイベントに限定する
4. アプリケーションサービスでトランザクションと業務順序を制御する

## まとめ

Cargo Tracker の Spring 実装は、単一デプロイでありながら BC 境界を明示したモジュラーモノリスです。次章では、この構成をイベント駆動（EDA）として捉え直し、パッケージングとイベント連携の設計をさらに掘り下げます。
