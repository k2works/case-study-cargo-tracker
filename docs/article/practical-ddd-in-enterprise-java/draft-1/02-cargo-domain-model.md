# 第 2 章：Cargo Tracker のドメインモデル

この章では、Cargo Tracker のドメインモデルを、実装クラスに対応づけて整理します。第 1 章の DDD 概念を、**どの BC に何を置いたか**へ具体化する章です。

## コアドメイン

Cargo Tracker のコアは、貨物予約から輸送完了までの整合を扱う `booking` BC です。`Cargo` 集約が予約状態・経路状態・追跡連携の中心を担います。

```java
public class Cargo {
    public static Cargo book(BookCargoCommand command) { ... }
    public void assignToRouting() { ... }
    public void assignItinerary(CargoItinerary itinerary) { ... }
    public void confirm(ClaimCode issued) { ... }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java`

## Cargo Tracker: サブドメイン／境界づけられたコンテキスト

| BC | 集約ルート |
| :--- | :--- |
| `booking` | `Cargo`, `BookingNotification`, `CancellationRequest` |
| `routing` | `Voyage`, `BookingRouteProposal` |
| `tracking` | `TrackingActivity` |
| `handling` | `HandlingActivity`, `CorrectionRequest`, `CustomsDeclaration` |
| `billing` | `Invoice`, `Reminder` |
| `estimation` | `Estimate` |
| `shipper` | `Shipper` |

参照（各 BC の集約定義）:

- `.../booking/domain/model/aggregates/package-info.java`
- `.../routing/domain/model/aggregates/package-info.java`
- `.../tracking/domain/model/aggregates/package-info.java`
- `.../handling/domain/model/aggregates/package-info.java`
- `.../billing/domain/model/aggregates/package-info.java`
- `.../estimation/domain/model/aggregates/package-info.java`
- `.../shipper/domain/model/aggregates/package-info.java`

## Cargo Tracker: ドメインモデル

### 集約

集約は BC ごとの一貫性境界です。外部からの変更は集約ルート経由に限定されます。たとえば `routing` では `Voyage` と `BookingRouteProposal` が独立した集約ルートです。

### 集約識別子

識別子は `record` で型として分離されています。

```java
public record BookingId(UUID value) { ... }
public record RoutingBookingId(UUID value) { ... }
public record TrackingBookingId(UUID value) { ... }
public record EstimateId(UUID value) { ... }
public record InvoiceId(String value) { ... }
```

代表参照:

- `.../booking/domain/model/valueobjects/BookingId.java`
- `.../routing/domain/model/valueobjects/RoutingBookingId.java`
- `.../tracking/domain/model/valueobjects/TrackingBookingId.java`
- `.../estimation/domain/model/valueobjects/EstimateId.java`
- `.../billing/domain/model/valueobjects/InvoiceId.java`

共有カーネル側では `ShipperId` を共通識別子として利用します。

```java
public record ShipperId(UUID value) { ... }
```

参照: `.../shared/domain/model/valueobjects/ShipperId.java`

### エンティティ

`routing` BC の `ProposedRoute` は、同一性とふるまいを持つエンティティとして実装されています。選択可否の判定を内部で持つことで、画面側に判定ロジックを漏らしません。

```java
public final class ProposedRoute {
    public boolean selectable() { ... }
    public String unselectableReason() { ... }
    public ProposedRoute withPriority(int newPriority) { ... }
}
```

参照: `.../routing/domain/model/entities/ProposedRoute.java`

### 値オブジェクト

`CargoSpecification` は、貨物種別と申告情報の整合を 1 か所で守る値オブジェクトです。

```java
public record CargoSpecification(
        CargoType cargoType,
        Weight weight,
        Dimensions dimensions,
        Quantity quantity,
        Description description,
        HazardousDeclaration hazardous,
        TemperatureRequirement temperature) {
    public static CargoSpecification create(...) { ... }
    public static CargoSpecification reconstruct(...) { ... }
}
```

参照: `.../booking/domain/model/valueobjects/CargoSpecification.java`

## Cargo Tracker: ドメインモデルの操作

更新操作はコマンドで始まり、集約メソッドで実行されます。参照操作はクエリサービスへ分離されます。

```java
public record BookCargoCommand(
        ShipperId shipperId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification) {}
```

参照: `.../booking/domain/model/commands/BookCargoCommand.java`

```java
public interface BookingQueryService {
    Page<BookingView> search(BookingSearchCriteria criteria, PageRequest page);
    Page<BookingView> findAwaitingRouting(PageRequest page);
    Optional<BookingView> findById(String bookingId);
}
```

参照: `.../booking/application/internal/queryservices/BookingQueryService.java`

## サガ

BC 横断の業務連鎖は、ドメインイベントとイベントハンドラで進めます。`BookingHandlingEventHandler` は荷役イベントを受けて、予約状態を同期します。

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(HandlingActivityRegisteredEvent event) {
    var result = applyService.apply(...);
    ...
}
```

参照: `.../booking/interfaces/events/BookingHandlingEventHandler.java`

イベントは `shared.domain.event` に定義されます。

```java
public record CargoRoutedEvent(UUID bookingId, String destinationUnlocode, LocalDate estimatedArrivalDate) {}
```

参照: `.../shared/domain/event/CargoRoutedEvent.java`

## ドメインモデルサービス

`routing` BC では `RouteSearchService` が、航海候補から経路提案を導くドメインサービスです。

```java
public class RouteSearchService {
    public List<ProposedRoute> search(RoutingCriteria criteria, List<Voyage> voyages) { ... }
}
```

参照: `.../routing/domain/model/RouteSearchService.java`

費用見積は `FreightEstimator` が担当し、探索ロジックから分離されています。

```java
public final class FreightEstimator {
    public Money estimate(RoutingWeight weight, int transitDays, RoutingCargoType cargoType) { ... }
}
```

参照: `.../routing/domain/model/FreightEstimator.java`

## ドメインモデルサービス設計

本実装の設計方針は次の分離です。

1. 集約: 状態遷移と不変条件
2. ドメインサービス: 複数オブジェクトにまたがる業務計算（例: 経路探索、概算運賃）
3. アプリケーションサービス: ユースケースの順序制御と外部連携

この分離により、集約を過大化させずに業務ルールをドメイン層に残せます。

## Cargo Tracker: DDD 実装

Cargo Tracker の DDD 実装は、次の対応で読み解けます。

- 境界づけられたコンテキスト = トップレベルパッケージ（`booking`, `routing`, ...）
- 集約・値オブジェクト = `domain.model`
- 永続化境界 = `domain.repository`（interface）と `infrastructure.repositories`（実装）
- BC 間連携 = ACL ポートまたはドメインイベント

結果として、業務モデルを中心に据えたまま、Spring / MyBatis / Web 層を外側へ分離できます。

## まとめ

この章では、Cargo Tracker のドメインモデルを BC・集約・識別子・エンティティ・値オブジェクト・サービスの観点で整理しました。

次章では、このモデルを Spring Platform 上のモジュラーモノリスとしてどう実装するかを扱います。
