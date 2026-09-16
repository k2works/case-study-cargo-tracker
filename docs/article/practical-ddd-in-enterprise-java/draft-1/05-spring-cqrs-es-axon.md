---
type: Article
title: "第 5 章：Spring Platform × CQRS/ES（Axon）"
description: "Axon Framework 5 によるイベントソーシングと CQRS で Cargo Tracker を構成する（draft-1）。集約・コマンド・イベント・投影・クエリハンドラ・連鎖の調整。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-16T00:00:00Z }
---

# 第 5 章：Spring Platform × CQRS/ES（Axon）

第 4 章では BC をプロセスごとに分け、境界を越える通信をイベントとサービス間 API に置き換えました。ただし**各サービスの中は現在状態の UPDATE のまま**です。この章では、その内側を入れ替えます。**保存するのは現在の状態ではなく、起きたことの列**になります。

**参照元の実装がまた変わります。**第 3 章が `docs/article/source/java-2`（モジュラーモノリス）、第 4 章が `docs/article/source/java-3`（マイクロサービス + RabbitMQ）だったのに対し、本章は `docs/article/source/java-4`（マイクロサービス + Axon Framework 5）です。**続きではなく別実装です。**サービスの分割は第 4 章とほぼ同じに揃えてあり、**差分が永続化と読み書きの分離だけになる**ように選ばれています。

> `java-3` が見送った Event Sourcing を、同じ分割の上で払う。
>
> 転記元: `docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`（`java-4` の ADR-0001 決定 1）

以降のコードのパスは `docs/article/source/java-4/apps/cargo-tracker/backend/` からの相対、一次資料（ADR・設計）のパスは `docs/article/source/java-4/` からの相対です。Java のパスは `<サービス>/src/main/java/com/example/cargotracker/<BC>/` を省いて記します（`bookingms/domain/model/aggregates/Cargo.java` は `bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java` を指します）。

## イベントソーシング

### 状態を持たないということ

第 3・4 章の集約は、**現在の状態を持ち、それを行として保存**していました。イベントは「保存したあとに他へ知らせるもの」で、消えても業務の記録は残ります。

イベントソーシングでは順序が逆になります。**集約はイベントを追記するだけ**で、状態は読み込み時にイベントを再生して作り直します。

```java
@EventSourced(idType = String.class, tagKey = "bookingId")
public class Cargo {

    private String bookingId;
    private BookingStatus bookingStatus;
```

転記元: `bookingms/domain/model/aggregates/Cargo.java`

フィールドはありますが、**これは保存されません**。保存されるのはイベントで、フィールドはイベントを適用した結果です。適用する場所は `@EventSourcingHandler` です。

```java
@EventSourcingHandler
void on(TrackingNumberIssuedEvent event) {
    this.bookingStatus = BookingStatus.TRACKING_ISSUED;
    this.trackingNumber = event.trackingNumber();
}
```

転記元: `bookingms/domain/model/aggregates/Cargo.java`

**ここに業務判断は入りません。**判断はコマンドハンドラが済ませ、イベントはその結果です。過去のイベントを再生するとき、当時の判断をやり直してはいけません。

### イベントは書き換えられない

この一点が、設計判断のほとんどを決めます。イベントは**永続化フォーマット**であり、追記しかできません。

| 規則 | 内容 |
| :--- | :--- |
| イベントは追記専用 | フィールドの削除・型変更をしない。要るなら新しいイベント型を足す |
| Upcaster で吸収 | 形を変えざるを得ないときは Upcaster を書き、旧形式のテストイベントを残す |
| シリアライザは Jackson | `record` をそのまま JSON にする |
| 型名はメタデータに載る | クラスの移動・改名は Upcaster を伴う。**パッケージ移動は「無料」ではない** |

転記元: `docs/design/cargo-tracker/architecture_backend.md`「イベント契約」

イベントの中身にも効きます。値オブジェクトをそのまま載せると、あとで不変条件を足したときに**過去のイベントが復元できなくなります**。

```java
/**
 * 値は素の型で載せる。値オブジェクトをそのまま載せると、あとで不変条件を足したとき
 * 過去のイベントが復元できなくなる（新しい検査を古いイベントが通らない）。
 */
public record CargoBookedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String shipperId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
```

転記元: `bookingms/domain/model/events/CargoBookedEvent.java`

第 3 章の集約は `RouteSpecification` を値オブジェクトのまま保持していました。イベントソーシングでは、その値オブジェクトを**17 個の素の項目へ平坦化**します。組み立て直す手順はイベント側の `of` に置きます——集約に書くと、受付と修正の 2 か所に同じ手順が並び、片方だけ直したときに食い違います。

あとから項目を足す場合も、**足す前に積まれたイベントには入っていません**。

```java
/**
 * <b>{@code null} を許す。</b> 足す前に積まれたイベントには入っていない——契約は
 * 追記専用で、過去のイベントは書き換えられない。読めなくなればその貨物は復元できなく
 * なるので、既定値（{@code null}）で読めるようにし、<b>重量が分からない貨物は請求を
 * 作らずに要確認へ出す</b>。足りない重量で安い請求を黙って出さない。
 */
```

転記元: `shared/contract/event/TrackingInitializedEvent.java`

## CQRS

第 3 章の実装も `commandservices` / `queryservices` にパッケージを分けてはいました。しかし**読む先は同じテーブル**で、分けていたのは呼び出し口だけです。

イベントソーシングを入れると、この分離は選択ではなく**必然**になります。イベント列は「予約 ID で 1 件引く」ことしかできず、「期限が近い順に並べて 20 件」には答えられないからです。答えるための表を別に作ります。

| 側 | 置き場 | 保存しているもの |
| :--- | :--- | :--- |
| 書く | `domain/model/aggregates` | イベント列（Axon Server の Event Store） |
| 読む | `infrastructure/projection` → `infrastructure/query` | 投影テーブル（PostgreSQL・MyBatis） |

**投影テーブルは正典ではありません。**捨てて作り直せるもので、正典はイベント列だけです。この性質が、あとの「投影はコマンドを送らない」という規律に繋がります。

## Axon Framework

### Axon コンポーネント

`java-4` が使う Axon の要素は次の 6 つです。

| 要素 | 採用する API | 置き場 |
| :--- | :--- | :--- |
| 集約の登録 | `@EventSourced(idType, tagKey)`・`@EntityCreator` | `domain/model/aggregates` |
| コマンドハンドラ | `@CommandHandler`（発行は引数の `EventAppender`） | 同上 |
| 状態復元 | `@EventSourcingHandler` | 同上 |
| コマンドの宛先 | `@TargetEntityId` | `domain/model/commands` |
| 投影 | `@EventHandler` + Processing Group（`pooled`） | `infrastructure/projection` |
| 問い合わせ | `@QueryHandler` + `QueryGateway` | `infrastructure/query` |

転記元: `docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`（決定 3）

**版は 5.1.0-RC2 に固定しています。**理由は好みではありません。

```toml
# Axon の版について（ADR-0001 決定 3・IT1 スパイク）:
#   axon-server-connector は Maven Central に 5.0.0 と 5.1.0-RC2 しか公開されていない。
#   コアだけ 5.3 に上げると CommandBusConnector / QueryBusConnector を解決できず
#   Axon Server に接続できない。starter / connector / axon-test は必ず同じ版で揃える。
#   版の上限は connector の公開状況が決めるので、上げるときはまず connector を確認する。
axon = "5.1.0-RC2"
```

転記元: `gradle/libs.versions.toml`

**Axon 4 の API は 5 系に存在しません。**`@Aggregate` / `@AggregateIdentifier` / `AggregateLifecycle.apply()` / `AggregateTestFixture`、そして `@ProcessingGroup` も無くなっています。書籍や既存記事の Axon 4 のコードは、そのままでは 1 行も通りません。

### Axon Framework のドメインモデルコンポーネント

#### コマンド／コマンドハンドラ

コマンドは素の `record` です。1 つだけ Axon のアノテーションが付きます。

```java
/**
 * {@code @TargetEntityId} が要る。集約に「作る側」（static）と「既にある側」の
 * ハンドラが両方あると、Axon は後者のためにコマンドから集約を特定できなければならない。
 * 付け忘れると {@code EntityIdResolutionException: found no identifiers} で落ちる。
 */
public record BookCargoCommand(
        @TargetEntityId String bookingId,
        String shipperId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification,
        String bookedBy) {
}
```

転記元: `bookingms/domain/model/commands/BookCargoCommand.java`

コマンドハンドラは集約のメソッドです。**イベントは `AggregateLifecycle.apply()` ではなく、引数で受け取る `EventAppender` に追記**します。

```java
@CommandHandler
public String book(BookCargoCommand command, EventAppender appender, Clock clock) {
    if (bookingId != null) {
        // 復元した集約が既に予約を持っているのに受け付けると、イベント列に
        // 予約が 2 本並び、どちらが正か決まらない。
        throw new IllegalTransition("予約 " + bookingId + " は既に受け付けています");
    }
    CargoValidation.validate(command, LocalDate.now(clock));
    appender.append(CargoBookedEvent.of(command.bookingId(), command.shipperId(),
            command.routeSpecification(), command.cargoSpecification(),
            command.bookedBy()));
    return command.bookingId();
}
```

転記元: `bookingms/domain/model/aggregates/Cargo.java`

**作成系も `static` にしていません。**これは実測から来た判断です。

> **static ではなくインスタンスのハンドラにしている。** 両方置くと、集約が
> 既に存在しても static のほうが呼ばれ、2 度目の受付が通る（IT2 で実測）。
> `@EntityCreator` が空の集約を用意するので、片方で両方を扱える。

転記元: `bookingms/domain/model/aggregates/Cargo.java`

`Clock` も引数で受けます。**業務タイムゾーンの「今日」で判断する**ためで、JVM 既定だと日本時間の朝 9 時より前に受け付けた予約で当日の期限が「過去」になる時間帯ができます。

#### イベント／イベントハンドラ

イベントも `record` です。集約と結び付けるのは `@EventTag` です。

```java
public record CargoBookedEvent(
        @EventTag(key = "bookingId") String bookingId,
```

転記元: `bookingms/domain/model/events/CargoBookedEvent.java`

**この 1 行が、この章でいちばん危険な行です。**

> **`@EventTag` が要る。** DCB はイベントに付いたタグで集約を復元する。
> `@EventSourced(tagKey)` は集約側の宣言でしかなく、イベント側で「どの項目が
> そのタグか」を言わないとタグが書かれない。付け忘れると、集約は毎回**空のまま
> 復元され**、状態を見る守り（2 度目の受付を断る、状態遷移の検査）が丸ごと
> 素通りする。それでもテストは緑になる（IT2 で実測）。

転記元: `bookingms/domain/model/events/CargoBookedEvent.java`

しかも、集約の単体テストでは判別できません。`AxonTestFixture` の `disableAxonServer()` ではタグによる復元が働かず、`given().event(...)` も `when()` の集約からは見えないためです。**状態を見る守りを足したら、実 Axon Server の統合テストで「壊して赤」を確かめる**——それ以外に確かめる手段がありません。

#### クエリハンドラ

問い合わせも `record` で、応答も `record` です。

```java
/**
 * 一覧（S20）。
 *
 * <p>{@code includeFinished} は「終了したものも表示」の操作に対応する。既定を
 * false にしているのは、精算済とキャンセルが混ざると一覧全体が「今日やること」
 * として信用されなくなるため（ui_design.md「一覧の既定条件」）。</p>
 */
public record FindBookingsQuery(int page, int size, boolean includeFinished, String q) {
}
```

転記元: `bookingms/infrastructure/query/BookingQueries.java`

#### サガ

**Axon 5 に Saga はありません。**これは「まだ移植されていない」ではなく、調べ切った結果です。

> **Axon 5 に Saga は存在しない。** 5.0.0・5.1.0-RC2・5.3.1 のいずれの jar にも
> `Saga`・`Deadline`・`@ProcessingGroup` を含むクラスが 1 つも無い（Axon 4 の概念）。
> 設計の Saga はすべて Reaction Handler で実装する

転記元: `docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`（決定 5 第 4 項）

置き換え方は後述します（「サガ」節）。ここで押さえるのは、**`@Saga` を前提にした設計文書がそのままでは実装できない**ことです。`java-4` でも設計書のほうが古く、IT7 の着手前検証で初めて食い違いが見つかっています。

#### Axon のディスパッチモデルコンポーネント

コマンドバス・クエリバス・イベントバスはいずれも Axon Server が担います。アプリケーション側が触るのは Gateway だけです。

```java
private final CommandGateway commandGateway;
private final QueryDispatcher queries;
```

転記元: `bookingms/interfaces/rest/BookingController.java`

`QueryDispatcher` は `QueryGateway` を包んだ共有カーネルのクラスで、待ち時間と例外の翻訳を 1 か所に寄せています。

```java
public <T> T query(Object query, Class<T> responseType) {
    try {
        return gateway.query(query, responseType).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("問い合わせが中断されました", e);
    } catch (Exception e) {
        throw translated(e);
    }
}
```

転記元: `shared/infrastructure/axon/QueryDispatcher.java`

**業務の断りを 500 に化けさせない**ためのクラスです。各 Controller が `catch (Exception)` を持つと、問い合わせ側が業務の判断で断ったことまで包まれ、画面には 500 が出ます。利用者は「壊れた」のか「入力が悪い」のかを判断できません。

### Axon のインフラストラクチャコンポーネント：Axon Server

Axon Server は 4 役を兼ねます——Command Bus・Event Bus・Query Bus・Event Store。第 4 章の RabbitMQ に相当しますが、**過去のイベントを保持する点**が決定的に違います。

設定は数行です。

```yaml
axon:
  axonserver:
    servers: ${AXON_SERVER:localhost:8124}
```

転記元: `bookingms/src/main/resources/application.yml`

ただし、この「数行で済む」見た目が罠になります。**繋がらなくても起動が成功します。**

> Axon Server に繋がらないとき、また context が DCB でないとき、
> アプリケーションは**起動を止めずに無限に再接続を試み続ける**。
> 起動が成功してしまうので、投影が永久に進まないことに誰も気づかない。

転記元: `shared/infrastructure/axon/AxonServerStartupCheck.java`

そのため起動時検査を自分で置いています。判定はログの検出ではなく context への問い合わせで行います——**ログの文言は版で変わるうえ、「出なかった」ことは検査にできない**からです。

自動設定に任せられない Bean もあります。

```java
/**
 * <b>なぜ手で組むか。</b> {@code TokenStore} は自動設定されず、無いと
 * {@code Could not find a mandatory TokenStore} で起動に失敗する。
 * {@code TransactionManager} は 1 つでなければならず、複数あると無音で
 * {@code NoTransactionManager} に落ちる。
 */
@Configuration
@ConditionalOnClass(DataSource.class)
public class AxonJdbcConfiguration {
```

転記元: `shared/infrastructure/axon/AxonJdbcConfiguration.java`

さらに Spring Boot との同居に 1 行の代償があります。

```yaml
  main:
    # Axon の axon.axonserver ConfigurationProperties が Spring Boot の
    # BoundConfigurationProperties と Bean 循環を作るため必須（IT1 スパイクで実測）。
    # Spring Boot の版を下げても回避できない。
    allow-circular-references: true
```

転記元: `bookingms/src/main/resources/application.yml`

**Event Store の運用も自分の責務に入ります。**`java-4` は差分エクスポートと再投入を実機で確かめており、そこで見つけたのが次の落とし穴です。

> **集約の復元はできない。** エクスポートにタグ（DCB の label）が含まれない。
> タグ無しで投入したイベントは、集約のタグで検索すると **0 件**になる。
> 件数と内容は一致しているのに、`@EventSourced(tagKey)` の集約だけが読めない。

転記元: `docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`（決定 5 第 7 項）

件数も内容も一致するので、検証としては合格に見えます。壊れるのは**復元後に初めてその集約へコマンドを送ったとき**です。だから復元演習の合格条件に「復元した集約へコマンドを 1 本送って通ること」を入れます。

## CQRS/ES としての Cargo Tracker

### Axon を用いた境界づけられたコンテキスト

サービス分割は第 4 章とほぼ同じです。違いは 2 つ——`simulationms` が業務の検証手段として残ること、そして**すべての業務 BC の集約がイベントソーシングになる**ことです。

| サービス | 集約 | 永続化 |
| :--- | :--- | :--- |
| bookingms | `Cargo` / `Shipper` / `Quotation` | イベント列 |
| routingms | `Voyage` | イベント列 |
| trackingms | `TrackingActivity` | イベント列 |
| handlingms | `HandlingActivity` / `CustomsDeclaration` | イベント列 |
| billingms | `Invoice` | イベント列 |
| authms | `User` | **状態保存** |

転記元: `docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`（決定 2）

**authms だけ外します。**現在状態だけが業務に要り、履歴は監査ログテーブルで足りるからです。

逆に、履歴が業務として要らない `Quotation` と `Voyage` も**あえてイベントソーシングにしています**。理由は業務ではなく比較のためだと明記されています——「履歴が要る集約だけ ES」にすると第 4 章との差分が集約ごとに違う形になり、代金を 1 つの表で並べられなくなるからです。そのうえで**見直しの発動条件を数値で置いています**。

> **IT2 終了時点で実績ベロシティが計画の 70% 未満なら、`Quotation` と `Voyage` を
> 状態保存（MyBatis の UPDATE）に落とす。**

転記元: 同上

「工数の問題が出たら」では検知できないので、判定できる形にする——**この置き方そのものが、学ぶに値する部分**です。

### 境界づけられたコンテキスト：成果物作成

デプロイの単位は Gradle のサブプロジェクトです。

```kotlin
// 業務サブプロジェクト（9 つ）
include("shared")
include("gatewayms")
include("authms")
include("bookingms")
include("routingms")
include("trackingms")
include("handlingms")
include("billingms")
include("simulationms")

// テスト専用サブプロジェクト（業務サービスの数には含めない）
include("contract-tests")
include("acceptance-tests")
```

転記元: `settings.gradle.kts`

各サービスの依存は短く済みます。

```kotlin
dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // Axon は starter と connector を必ず同じ版で入れる（ADR-0001 決定 3）。
    // connector は starter の推移的依存に含まれないので明示する。
    implementation(libs.bundles.axon)
```

転記元: `bookingms/build.gradle.kts`

第 4 章の `spring-boot-starter-amqp` が消え、Axon に置き換わっています。**配送経路は Axon Server 一本**で、サービスは互いの URL を知りません。

### 境界づけられたコンテキスト：パッケージ構造

4 層は第 3・4 章から変わりません。変わるのは各層の中身です。

| レイヤー | 置くもの | 置かないもの |
| :--- | :--- | :--- |
| `domain` | `@EventSourced` の集約、コマンド／イベントの `record`、値オブジェクト、ドメインサービス | Spring・MyBatis・Axon の設定 |
| `application` | `reaction` の Reaction Handler、ACL ポート（interface）、Gateway の利用 | SQL、HTTP |
| `infrastructure` | `@EventHandler` の投影、`@QueryHandler`、MyBatis Mapper、Axon の設定 | 業務ルール、**コマンドの送信** |
| `interfaces` | REST Controller、DTO、DTO とコマンドの変換 | ドメインの直接操作（必ず Gateway を通す） |

転記元: `docs/design/cargo-tracker/architecture_backend.md`「レイヤー責務一覧」

**ドメイン層が Axon のアノテーションに依存すること**は許容しています。`@CommandHandler` / `@EventSourcingHandler` はコンパイル時依存だけで、実行時のフレームワーク呼び出しを持ち込みません。問題は `@EventSourced` です——これは Spring の stereotype（メタアノテーションに `@Component`）を持ちます。

それでも許すのは、**これ無しでは集約が Command Bus に登録されない**ためです。`@Bean EventSourcedEntityModule` で代替すると二重登録になり、`@EventSourcedEntity` は 5.1.0-RC2 に存在しません。そこで ArchUnit の許可リストに **`org.axonframework.extension.spring.stereotype.EventSourced` の 1 型だけ**を明示し、`org.springframework..` への直接依存は引き続き禁止しています。

**「例外を作る」ことと「例外を検査に落とす」ことは別です。**許可リストに無い Axon の型をドメインが使えば赤になります。

イベントの置き場にも規律があります。原則**サービスの中**（`domain/model/events`）に置き、他サービスが購読するものだけを `shared/contract/event` に置きます。Event Sourcing ではイベントが集約の永続化フォーマットであり、**その所有者は集約を持つサービスだから**です。

```java
/**
 * 契約イベントではない（bookingms の内側だけで読む）。他サービスが必要とするのは
 * 追跡番号が出たあと（{@code TrackingNumberIssuedEvent}）なので、ここでは
 * {@code shared/contract} に置かない。置くと、読む側の無い契約を先に敷くことになる。
 */
```

転記元: `bookingms/domain/model/events/CargoBookedEvent.java`

契約の名簿は**名前で固定**します。本数だけを数えると 1 本足して 1 本消しても通り、**うっかり契約へ移したイベント**が素通りするためです。

### Axon を用いたドメインモデルの実装

#### 集約と状態

`Cargo` が持つフィールドは、**「イベント列から復元して、判断に使う分」だけ**です。表示のための値は持ちません。何を持つかは、そのつど理由とともに決まります。

```java
/**
 * 荷主。<b>発行のイベントに載せる</b>（US18）。
 *
 * <p>受付のイベントには最初から載っていたが、集約は保持していなかった
 * （{@code book()} を通り抜けるだけ）。持たないと、発行のときに誰の貨物か
 * 分からず、trackingms は荷主を知る手段を持たない。</p>
 */
private String shipperId;
```

転記元: `bookingms/domain/model/aggregates/Cargo.java`

**投影に尋ねて済ませない**という判断も、ここで繰り返し現れます。

```java
/**
 * 未決着のキャンセル申請（不変条件 10）。<b>高々 1 件</b>。
 *
 * <p><b>集約が持つ。</b> 投影に尋ねると、投影が追いついていないあいだは
 * 申請できない／二重に申請できるの両方が起こる。</p>
 */
private CancellationRequest pendingCancellation;
```

転記元: 同上

これは CQRS 特有の落とし穴です。投影は**結果整合**なので、不変条件の判断材料にしてはいけません。判断に要るものは集約が持ちます。

#### コマンドの処理

判定は集約に書き直さず、値オブジェクトの述語を呼びます。

```java
@CommandHandler
public String requestRouting(RequestRoutingCommand command, EventAppender appender) {
    requireBooked(command.bookingId());
    // 遷移先で判断しない。ROUTE_PROPOSED への自己遷移は経路の確定と条件の調整の
    // もので、引き渡しではない。述語を呼ぶ（BookingStatus#canRequestRouting）。
    if (!bookingStatus.canRequestRouting()) {
        throw new IllegalTransition(
                "状態 " + bookingStatus.label() + " の予約は経路設計へ引き渡せません");
    }
    appender.append(new RoutingRequestedEvent(command.bookingId(), command.requestedBy()));
    return command.bookingId();
}
```

転記元: `bookingms/domain/model/aggregates/Cargo.java`

**ここで `if (status == PRELIMINARY)` と書くと、遷移表と集約の判断が二重になります。**画面も同じ述語を写して操作の出し分けを決めるので、二重になった時点で「出ているのに押すと断られるボタン」が生まれます。

外から来た値も信じません。経路の確定では、探索が作った候補であっても集約が検算します。

```java
/**
 * <b>旅程が経路仕様を満たすかは集約が見る</b>（不変条件 5）。「候補は探索が
 * 作ったのだから正しい」としない——探索と集約は別の判断で、API を直接叩く経路も
 * ある。
 */
```

転記元: 同上

#### イベントの発行と状態の維持

コマンドハンドラは判断してイベントを追記し、`@EventSourcingHandler` がそれを状態へ反映します。**最初のコマンドと後続のコマンドで、経路は変わりません。**

- 最初のコマンド：`@EntityCreator` が空の集約を作り、`book()` が `bookingId == null` を見て受け付ける
- 後続のコマンド：Axon がタグでイベント列を引き、`@EventSourcingHandler` を順に当てて状態を作り、そこへコマンドが届く

`@EventSourcingHandler` には業務判断を書かない、と先に述べました。ただし**書かざるを得ない分岐**もあります。

```java
@EventSourcingHandler
void on(ShipperNotifiedEvent event) {
    if (bookingStatus == BookingStatus.IN_TRANSIT) {
        // **輸送中は状態を動かさない**（US28 §受入基準 6）。記録だけ残す。
        return;
    }
    this.bookingStatus = BookingStatus.ROUTE_NOTIFIED;
}
```

転記元: 同上

これは判断ではなく、**イベントの意味そのもの**です。通知は出来事であって状態の巻き戻しではない——誤配を組み直したあとの再通知で `ROUTE_NOTIFIED` へ戻すと、輸送中の貨物が「経路を通知しただけ」に見え、確定も追跡番号の発行もやり直しになります。

#### 集約の投影

投影は `@EventHandler` を持つ普通の Spring コンポーネントです。

```java
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class CargoProjection {
```

転記元: `bookingms/infrastructure/projection/CargoProjection.java`

規律は 2 つあります。

**1 つ目：投影はコマンドを送りません。**送るとリプレイのたびに副作用が再実行されます。これはレイヤー責務表にも「`infrastructure` にコマンドの送信を置かない」として書かれています。

**2 つ目：処理の列を細かく切ります。**

> 既定では列が全体で 1 本なので、1 件の毒で**無関係の予約のイベントまで退避される**
> （IT12 のクラスタ E2E で 4 件のうち 3 件が巻き添え）。退避先は順序を守るために
> 「同じ列の後続」も退避するので、**列の切り方がそのまま被害の範囲になる**。

転記元: 同上

かといって予約より細かくは切れません。同じ予約の中では順序が要る（訂正は登録より後に効かなければならない）からです。

投影は**落ちてよい場所ではありません**。書けなかったときに黙らないようにします。

```java
int updated = cargos.updateRoutingRequested(event.bookingId(), ...);
if (updated == 0) {
    log.warn("経路設計の依頼を書ける予約が投影に無い: bookingId={}", event.bookingId());
}
```

転記元: 同上

弾いたものは**要確認一覧へ出します**。宛先はロールで分けます——気づく手段は、その人が次に取れる行動へ繋がらなければ意味がないからです。

設定側にも列挙の規律があります。

```yaml
axon:
  eventhandling:
    processors:
      # **退避先は Processor ごとに明示する**（ADR-0014）。`"[..default]"` は
      # この版では効かない（実測。DLQ が付かず、1 件で全部止まる形に戻る）。
      "[com.example.cargotracker.booking.infrastructure.projection]":
        mode: pooled
        dlq:
          enabled: true
      "[com.example.cargotracker.booking.application.reaction]":
        mode: pooled
        dlq:
          enabled: true
```

転記元: `bookingms/src/main/resources/application.yml`

**Processing Group はパッケージ名で分けます**（`@ProcessingGroup` が無いため）。投影と Reaction Handler を別の Group にするために、パッケージを分けているわけです。そして書き忘れを人の注意で防がないよう、**設定ファイルを走査する検査**を置いています——`@EventHandler` を持つパッケージがここに載っているか、載っているものに `dlq` が付いているか、の 2 つです。

#### クエリハンドラ

クエリハンドラは投影テーブルだけを見ます。

```java
/** 予約の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class BookingQueryHandler {
```

転記元: `bookingms/infrastructure/query/BookingQueryHandler.java`

ただし**判定はテーブルの上で書き直しません**。選択肢を作るような問い合わせは、集約と同じ関数を呼びます。

```java
/**
 * 陸揚げ地の選択肢（S23 / US30 §受入基準 5）。
 *
 * <p><b>集約と同じ関数から作る。</b> 画面で組み立てると、出ているのに押すと
 * 断られる港が生まれる（IT5 のレビューで一度出た形）。投影の旅程と現在地を
 * 材料にして、判定そのものは {@code DischargeCandidates} に任せる。</p>
 */
```

転記元: 同上

**CQRS は「読み側で好きに書いてよい」ではありません。**読み側が本番の判定を写すと、写した瞬間から食い違いが始まります。

#### サガ

設計が Saga と呼んでいた調整役は、すべて `application/reaction` の Reaction Handler です。

```java
/**
 * 予約 → 追跡開始の連鎖（US14 / ADR-0010）。<b>Reaction Handler の 1 本目</b>。
 *
 * <p><b>Saga ではない。</b> Axon 5 に Saga の API が無い（ADR-0001 決定 6）。段の数だけ
 * ハンドラを並べ、途中経過は {@code process_state} に持つ。Saga のストアに直列化して
 * 埋めるのと違い、<b>止まった位置がそのまま SQL で読める</b>。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class BookingReactionHandler {
```

転記元: `bookingms/application/reaction/BookingReactionHandler.java`

置き換えの対応は次のとおりです。

| Saga の機能 | 置き換え |
| :--- | :--- |
| `@StartSaga` | 1 段目が `process_state` に `RUNNING` の行を作る |
| `associateWith()` | `process_state` の `process_type` + `process_id` |
| `@EndSaga` | 最後の段で `status = 'COMPLETED'`。**行は消さない** |
| Deadline | `RUNNING` かつ 24 時間より古い行を運用ジョブで走査 |
| Saga Store | `process_state`。**止まった位置がそのまま SQL で読める** |

転記元: `docs/design/cargo-tracker/architecture_backend.md`「Saga の各機能の置き換え」

**これは代替品ではなく、Axon 5 が勧めている形**だと ADR は書いています。自分で持つほうがよい理由は 4 つ——状態が見える・直列化の事故が起きない・テストが単純・置き場を業務の都合で選べる。そして**Saga が戻ってきても、この 4 つを上回らない限り移らない**と、再評価の発動条件まで先に決めてあります（`SagaIsStillAbsentTest` が「Saga が現れたら赤」にします）。

1 段目の実装はこうなります。

```java
@EventHandler
public void on(TrackingNumberIssuedEvent event) {
    var state = processes.start(PROCESS_TYPE, event.bookingId(), STEP_INITIALIZE_TRACKING,
            TOTAL_STEPS, Map.of("trackingNumber", event.trackingNumber()));
    if (!state.isRunning()) {
        // 終わった連鎖に遅れて届いた。送り直すと追跡が作り直される。
        return;
    }

    try {
        commands.sendAndWait(new InitializeTrackingCommand(...));
        // 送れたところまでを 1 段目の完了とする。**送る前に進めない**——届いて
        // いないのに「1 段終わった」と読めると、滞留の走査から漏れる。
        processes.advance(PROCESS_TYPE, event.bookingId(),
                STEP_INITIALIZE_TRACKING, STEP_TRACKING_INITIALIZED);
    } catch (RuntimeException e) {
        // **握りつぶさない。** 例外を投げ直すと Axon の Event Processor が
        // 再試行する。上限を超えたときだけ補償へ落とす（ADR-0010 決定 4）。
        int attempts = attemptsOf(state) + 1;
        processes.recordAttempt(PROCESS_TYPE, event.bookingId(), attempts);
        if (attempts < MAX_ATTEMPTS) {
            throw e;
        }
        compensate(event, e);
    }
}
```

転記元: `bookingms/application/reaction/BookingReactionHandler.java`

**補償は「取り消す」ことではなく「やり直せる状態に戻す」ことです。**追跡番号の発行だけを取り消し、予約は `CONFIRMED` に留めます——キャンセルではないので、経路設計者がもう一度発行できるようにするだけです。

そして補償したことを**誰に知らせるか**が、設計から変わっています。

> 設計（architecture_backend.md）は「追跡管理者の要確認一覧に写す」と書いていたが、
> **追跡管理者には打つ手が無い**——追跡番号を発行し直せるのは経路設計者だけである。
> 気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。

転記元: 同上

**Reaction Handler は同期クエリを呼びません。**`QueryDispatcher` で待つと Processing Group が止まるためで、ArchUnit の規則が `QueryGateway` の直接利用だけでなく `QueryDispatcher` 越しの呼び出しも禁じています。

### 実装のまとめ

イベントソーシングが実際に変えたのは、次の 5 点です。

| 観点 | 第 4 章（状態保存 + RabbitMQ） | 第 5 章（Event Sourcing + Axon） |
| :--- | :--- | :--- |
| 集約の永続化 | 現在状態を UPDATE | イベントを追記し、再生して復元 |
| 読み取り | 同じテーブルを SELECT | 投影テーブル（結果整合・作り直せる） |
| イベントの位置づけ | 保存のあとの通知 | **保存そのもの**（追記専用・改名に代償） |
| 調整役 | メッセージハンドラ | Reaction Handler + `process_state` |
| 失敗の扱い | 再送とべき等 | 再試行 → 上限 → **補償と要確認一覧** |

代金も明確です。**緑になるのに何も見ていないテスト**が、この方式では 2 度出ました（`@EventTag` の付け忘れ、エクスポートからの復元）。どちらも件数と内容は一致し、単体テストは緑です。**実 Axon Server での統合テストと、クラスタでの E2E を検査として持たない限り、この方式は安全になりません。**

### Axon を用いたドメインモデルサービスの実装

#### 受信サービス：REST API

Controller は HTTP と業務の境界のままです。変わるのは、ユースケースクラスではなく **Gateway を呼ぶ**点です。

```java
@PostMapping
public ResponseEntity<BookCargoResponse> book(@Valid @RequestBody BookCargoRequest request,
        @RequestHeader(name = "X-Auth-Username", required = false) String username) {
    String bookingId = UUID.randomUUID().toString();

    BookCargoCommand command = new BookCargoCommand(
            bookingId,
            request.shipperId(),
            CargoSpecificationAssembler.from(request),
            new RouteSpecification(
                    Location.of(request.originUnLocode()),
                    Location.of(request.destinationUnLocode()),
                    request.arrivalDeadline()),
            username);
    commandGateway.sendAndWait(command);
```

転記元: `bookingms/interfaces/rest/BookingController.java`

**識別子は Controller が採ります。**集約はまだ存在せず、コマンドの宛先を決めるのは送る側だからです。

読み取りは `QueryDispatcher` 経由で、同じ Controller の中に並びます。**書く側と読む側が同じクラスに同居していてもよい**——分けるのは呼び出し口ではなくモデルのほうです。

#### 受信サービス：イベントハンドラ

イベントを受ける口は 2 種類あり、**置き場で役割が決まります**。

| 置き場 | 役割 | コマンドを送るか | リプレイ対象か |
| :--- | :--- | :--- | :--- |
| `infrastructure/projection` | 投影を書く | **送らない** | する |
| `application/reaction` | 連鎖を進める | 送る | **しない** |

同じ Group にすると、投影のリプレイでコマンドが再送され、追跡が作り直されます。**パッケージが Processing Group そのもの**なので、置き場所を間違えることが設定の誤りと同義になります。

## アプリケーションサービス

第 3・4 章にあった「アプリケーションサービス」というクラス群は、この方式では**ほとんど消えます**。ユースケースの手順は集約のコマンドハンドラと Reaction Handler に分かれ、`application` パッケージに残るのは ACL ポートの定義と、いくつかの計算だけです。

```java
/**
 * ACL ポート（利用側が定義する interface）。
 */
package com.example.cargotracker.booking.application.port;
```

転記元: `bookingms/application/port/package-info.java`

ACL の実装は `infrastructure/acl` に置き、**Axon の Query Bus 越し**に他サービスへ問い合わせます。

```java
@Override
public RouteCandidates find(RouteSearchRequest request) {
    RouteCandidatesResponse response;
    try {
        response = queries.query(toQuery(request), RouteCandidatesResponse.class);
    } catch (BusinessRuleViolation e) {
        // 経路設計側が「知らない港・種別」と断った。障害ではないので、そのまま通す。
        throw e;
    } catch (RuntimeException e) {
        // 相手が居ない（NoHandlerForQueryException）・時間切れ・通信の失敗。
        throw new RouteSearchUnavailable("経路設計サービスに問い合わせられませんでした", e);
    }
    if (response == null) {
        // ハンドラが null を返すことはないが、返ったなら「0 件」ではない。
        throw new RouteSearchUnavailable("経路設計サービスから応答がありません", null);
    }
```

転記元: `bookingms/infrastructure/acl/QueryBusRouteCandidateFinder.java`

**落ちているときに空リストを返しません。**空にすると「候補が無い」と読まれ、経路設計者は直らない条件を変え続けます。Query Bus を選んだ理由の 1 つがここにあります——REST の接続エラーと違い、**Axon Server が「誰も居ない」と明示的に答える**からです。

ACL の責務は第 3・4 章と変わりません。契約 DTO を自 BC の型へ組み直します。ただし**数え直しは慎重に**分けています。

> **候補の超過日数は routingms が数えたものを写す。** ここで数え直すと、探索が使った
> 期限と画面に出す超過日数がずれる。**確定するときは `Cargo` が数え直す。** 呼ぶ側が
> 持ってきた数を信じないためで、しかも予約の期限は条件調整で動きうる。
> 2 か所あるのは重複ではなく、**答えるべき問いが違う**。

転記元: 同上

## まとめ

第 5 章は、第 4 章のサービス分割をそのままに、**各サービスの内側を状態保存からイベント列へ入れ替えました**。

得たものは 3 つです。**起きたことが全部残る**（誤配も取り消しも、結果ではなく経緯として読める）。**読み取りモデルを業務の都合で何枚でも作れる**（一覧・作業一覧・要確認一覧が同じ表を奪い合わない）。**サービスが互いの URL を知らない**（配送経路が Axon Server 1 本に揃う）。

払ったものも 3 つです。**イベントが契約になる**（改名も項目追加も、過去のイベントが読めるかどうかで縛られる）。**結果整合が業務判断に入り込む**（不変条件の材料を投影に尋ねられない）。そして**緑に見えて何も見ていないテストが増える**（`@EventTag`、復元演習、`disableAxonServer()`）。

最後の 1 つがこの方式の本質的な代金です。第 3 章の実装なら、集約の単体テストが落ちれば守りが壊れたと分かります。この方式では、**守りが丸ごと外れていても単体テストは緑になります**。実 Axon Server での統合テストとクラスタでの E2E を、選択肢ではなく**必須の検査**として持てるかどうか——導入判断はフレームワークの好みではなく、そこにあります。

次章では、ここまでの 3 方式（モジュラーモノリス、EDA、CQRS/ES）を比較し、採用判断の基準を整理します。
