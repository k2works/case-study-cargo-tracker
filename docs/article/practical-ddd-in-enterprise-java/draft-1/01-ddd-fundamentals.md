# 第 1 章：ドメイン駆動設計

この章では、Cargo Tracker を題材に、DDD の最小セットを実装と対応づけて整理します。ここでの目的は用語の暗記ではなく、**業務の境界とモデルをコード上でどこに置くか**を明確にすることです。

## この章のゴール

1. 問題空間とサブドメインを分離して説明できること
2. 集約・エンティティ・値オブジェクトの責務分担を説明できること
3. コマンド／クエリ／イベント／サガを、Cargo Tracker の実装に対応づけられること

## DDD の概念

### 問題空間／ビジネスドメイン

Cargo Tracker の問題空間は、貨物輸送業務における以下の一連の流れです。

- 見積
- 予約
- 経路設計
- 追跡
- 荷役
- 精算

ここで重要なのは、技術的なレイヤ（Web、DB、メッセージング）ではなく、**業務上の責務の切れ目**でモデルを分けることです。

### サブドメイン／境界づけられたコンテキスト

本実装では、業務責務ごとに BC を分けています。

| BC | 主責務 |
| :--- | :--- |
| `booking` | 予約と状態遷移 |
| `routing` | 経路候補と航海日程 |
| `tracking` | 輸送状況と例外追跡 |
| `handling` | 荷役作業記録 |
| `billing` | 請求・精算 |
| `estimation` | 輸送見積 |
| `shipper` | 荷主情報 |

BC 間の連携は直接参照ではなく、ACL ポートまたはドメインイベントを経由します。これにより、業務言語の境界をコード上でも保てます。

## ドメインモデル

### 集約／エンティティオブジェクト／値オブジェクト

`booking` BC では `Cargo` が集約ルートです。状態変更は setter ではなく、業務操作メソッドで行います。

```java
public class Cargo {
    private final BookingId bookingId;
    private CargoProgress progress;

    public static Cargo book(BookCargoCommand command) { ... }
    public void assignToRouting() { ... }
    public void confirm(ClaimCode issued) { ... }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java`

識別子は値オブジェクトとして `record` で表現されます。

```java
public record BookingId(UUID value) {
    public BookingId {
        if (value == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/valueobjects/BookingId.java`

### ドメインルール

予約状態の遷移規則は `BookingStatus` に閉じ込め、表形式の遷移ルールを Enum 内で実行可能にしています。

```java
public enum BookingStatus {
    PRELIMINARY, ROUTE_PROPOSED, CONFIRMED, TRACKING_ISSUED, IN_TRANSIT, DELIVERED, SETTLED, CANCELLED;

    public boolean canTransitionBy(BookingCommandType command) { ... }
    public BookingStatus transitionBy(BookingCommandType command) { ... }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/valueobjects/BookingStatus.java`

この構成により、画面のボタン表示条件とドメインの遷移条件を同じ規則に統一できます。

### コマンド／クエリ

更新要求はコマンド、参照要求はクエリで分離します。`BookCargoCommand` は予約登録に必要な入力だけを保持します。

```java
public record BookCargoCommand(
        ShipperId shipperId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification) {}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/commands/BookCargoCommand.java`

参照側は `BookingQueryService` が担い、検索要件をクエリ専用 API に集約します。

```java
public interface BookingQueryService {
    Page<BookingView> search(BookingSearchCriteria criteria, PageRequest page);
    Page<BookingView> findAwaitingRouting(PageRequest page);
    Optional<BookingView> findById(String bookingId);
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/queryservices/BookingQueryService.java`

### イベント

BC をまたぐ状態通知はドメインイベントで行います。たとえば経路確定時には `CargoRoutedEvent` を発行します。

```java
public record CargoRoutedEvent(
        UUID bookingId,
        String destinationUnlocode,
        LocalDate estimatedArrivalDate) {
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/domain/event/CargoRoutedEvent.java`

### サガ

複数 BC にまたがる一連の処理は、イベントハンドラとアプリケーションサービスの連携で進めます。`BookingVoyageRescheduledEventHandler` は、航海更新イベントを受けて予約側の旅程スケジュール同期を起動します。

```java
@Component
public class BookingVoyageRescheduledEventHandler {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(VoyageRescheduledEvent event) {
        syncService.sync(event);
    }
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/events/BookingVoyageRescheduledEventHandler.java`

このように、単一トランザクションで閉じない業務連鎖を段階的に整合させる構成が、実装上のサガとして機能します。

## まとめ

この章では、DDD の基本要素を Cargo Tracker の実装へ対応づけました。

- 境界は技術ではなく業務責務で切る
- 集約は業務操作で状態を変える
- ルールはモデル内部で一元化する
- 越境はコマンド／クエリ分離とイベント連携で扱う

次章では、これらの前提を Cargo Tracker 固有のコアドメインとモデル設計に具体化します。
