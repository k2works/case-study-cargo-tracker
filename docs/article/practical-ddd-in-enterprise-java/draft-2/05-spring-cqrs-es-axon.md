---
type: Article
title: "第 5 章：イベントを正典にする — CQRS / Event Sourcing 版の Cargo Tracker"
description: "集約の保存を現在状態からイベント列へ入れ替えたとき何が変わるかを、Axon Framework 5 の実装から示す。緑のまま外れる守りと、それを検査に落とす方法。"
tags: [article, practical-ddd-in-enterprise-java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-16T00:00:00Z }
---

# 第 5 章：イベントを正典にする — CQRS / Event Sourcing 版の Cargo Tracker

前章は、BC をプロセスに分けたときイベント駆動が何を新しく要求するかを見ました。ただし**各サービスの内側は、第 3 章と同じ**です。集約は現在の状態を持ち、MyBatis がその行を UPDATE します。イベントは保存したあとの通知で、消えても業務の記録は残ります。

この章で入れ替えるのは、その内側です。**保存するのは現在の状態ではなく、起きたことの列**になります。

## 参照元がまた変わります

| | 第 1〜3 章 | 第 4 章 | 本章 |
| :--- | :--- | :--- | :--- |
| 参照元 | [`source/java-2/`](../../source/java-2) | [`source/java-3/`](../../source/java-3) | [`source/java-4/`](../../source/java-4) |
| 構成 | モジュラーモノリス | マイクロサービス | マイクロサービス |
| BC 間の配送 | `ApplicationEventPublisher` | RabbitMQ | Axon Server |
| 同期の越境 | ACL ポート（メソッド呼び出し） | REST（ACL 経由） | Axon Query Bus（ACL 経由） |
| 集約の保存 | 現在状態を UPDATE | 現在状態を UPDATE | **イベント列（Event Store）** |
| 読み取り | 同じテーブルを SELECT | 同じテーブルを SELECT | **投影テーブル** |

**3 本目の別実装です。** 第 4 章と同じく、続きではありません。

ただし第 4 章との関係だけは、偶然ではありません。**サービスの分割を意図的に揃えてあります。**

> 根拠は「**第 4 章とプロセスの形を揃える**」ことである。第 4 章（`java-3`）はマイクロサービスで「プロセスを越えるイベント」を扱った。第 5 章も同じサービス分割にすれば、第 4 章との差分は永続化（現在状態の UPDATE → イベント列）と読み書きの分離だけになり、それがそのまま Event Sourcing の代金として第 6 章で比較できる。

引用元: [`docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`](../../source/java-4/docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)（決定 1・設計文書からの引用）

**この章は、前稿（draft-1）で構想だったものです。** draft-1 の第 5 章は冒頭で「現行リポジトリには Axon 実装は含まれていません」と断ったうえで、移行時の設計差分を書いていました。本稿はそれを引き継がず、参照元が収録されてから書き直しています。**構想で埋めた章と、実装から書いた章が何を変えるか**は、この章の最後で触れます。

## この章のゴール

1. 集約の保存をイベント列に替えたとき、**何が判断の材料でなくなるか**を説明できること
2. **守りが丸ごと外れてなおテストが緑になる**種類の欠陥を挙げ、それを静的な検査に落とす方法を辿れること
3. 設計文書が「決めた」と書いていることと、実装が「守っている」ことの差をどこで埋めているかを言えること

## サービスの分割は前章とほぼ同じ

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

引用元: [`apps/cargo-tracker/backend/settings.gradle.kts`](../../source/java-4/apps/cargo-tracker/backend/settings.gradle.kts)

第 4 章の `settings.gradle` と並べると、業務サービスの顔ぶれは同じです。増えているのは**テスト専用のサブプロジェクト 2 つ**で、しかもコメントが「業務サービスの数には含めない」と明記しています。数え方まで書いてあるのは、**本文のどこかに「8 サービス」と書いた人がいる**からです。

依存も 1 か所に固定されています。

```toml
# Axon 系はこの 1 つの ref だけを参照する（ArchUnit/ビルド検査で固定）
axon = "5.1.0-RC2"
```

引用元: [`apps/cargo-tracker/backend/gradle/libs.versions.toml`](../../source/java-4/apps/cargo-tracker/backend/gradle/libs.versions.toml)

版が固定されているのは好みではありません。ADR-0001 の調査時点（2026-09-02）で `axon-server-connector` が Maven Central に 5.0.0 と 5.1.0-RC2 しか公開されておらず、コアだけ版を上げると接続できなくなるためです。**RC を本番構成に採る判断**を、ADR はその理由ごと残しています。

## 何が入れ替わったか

### 集約は状態を保存しない

```java
@EventSourced(idType = String.class, tagKey = "bookingId")
public class Cargo {
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java)

フィールドはありますが、**保存されません**。保存されるのはイベントで、フィールドはイベントを適用した結果です。

```java
    /**
     * 予約を受け付ける。
     *
     * <p><b>static ではなくインスタンスのハンドラにしている。</b> 両方置くと、集約が
     * 既に存在しても static のほうが呼ばれ、2 度目の受付が通る（IT2 で実測）。
     * {@code @EntityCreator} が空の集約を用意するので、片方で両方を扱える。</p>
     */
    @CommandHandler
    public String book(BookCargoCommand command, EventAppender appender, Clock clock) {
        if (bookingId != null) {
            // 復元した集約が既に予約を持っているのに受け付けると、イベント列に
            // 予約が 2 本並び、どちらが正か決まらない。
            throw new IllegalTransition("予約 " + bookingId + " は既に受け付けています");
        }
        // 業務タイムゾーンの「今日」で判断する。JVM 既定だと、日本時間の朝 9 時より
        // 前に受け付けた予約で当日の期限が「過去」になる時間帯ができる。
        CargoValidation.validate(command, LocalDate.now(clock));
        appender.append(CargoBookedEvent.of(command.bookingId(), command.shipperId(),
                command.routeSpecification(), command.cargoSpecification(),
                command.bookedBy()));
        return command.bookingId();
    }
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java)

読むべきは 2 つです。

**1 つ目：作成系のハンドラも `static` にしていません。**Axon 4 の作法（作成系は static なファクトリ）をそのまま写すと、**集約が既に存在しても static のほうが呼ばれ、2 度目の受付が通ります**。Javadoc が「IT2 で実測」と書いているのは、そう書いて壊れたからです。

**2 つ目：`Clock` を引数で受けています。**業務タイムゾーンの「今日」で判断するためで、JVM 既定だと日本時間の朝 9 時より前に受け付けた予約で当日の期限が「過去」になる時間帯ができます。第 3 章で見た「時刻を注入する」判断が、ここでは集約のシグネチャに現れています。

状態を作るのは別のメソッドです。

```java
    @EventSourcingHandler
    void on(TrackingNumberIssuedEvent event) {
        this.bookingStatus = BookingStatus.TRACKING_ISSUED;
        this.trackingNumber = event.trackingNumber();
    }
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java)

**ここに業務判断は入りません。**判断はコマンドハンドラが済ませ、イベントはその結果です。過去のイベントを再生するとき、当時の判断をやり直してはいけません——やり直すと、規則を変えた日を境に**過去の予約の状態が変わります**。

### イベントは永続化フォーマットになる

```java
public record CargoBookedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String shipperId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        int quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC,
        String bookedBy) {
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/events/CargoBookedEvent.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/events/CargoBookedEvent.java)

値オブジェクトではなく**素の型を 17 個**並べています。理由は Javadoc にあります——値オブジェクトを載せると、あとで不変条件を足したときに**過去のイベントが復元できなくなる**（新しい検査を古いイベントが通らない）。

第 3・4 章のイベントは「保存したあとの通知」でした。消えても業務の記録は DB の行に残ります。ここでは逆で、**イベントが消えたら業務の記録が消えます**。だから形を変えられません。

| 規則 | 内容 |
| :--- | :--- |
| イベントは追記専用 | フィールドの削除・型変更をしない。要るなら新しいイベント型を足す |
| Upcaster で吸収 | 形を変えざるを得ないときは Upcaster を書き、旧形式のテストイベントを残す |
| 型名はメタデータに載る | クラスの移動・改名は Upcaster を伴う。**パッケージ移動は「無料」ではない** |

引用元: [`docs/design/cargo-tracker/architecture_backend.md`](../../source/java-4/docs/design/cargo-tracker/architecture_backend.md)「イベント契約」（設計文書からの引用）

第 4 章の契約は「発行側と購読側が同じ形を読むこと」でした。ここではそれに加えて、**過去の自分が書いたものを将来の自分が読めること**が契約に入ります。

### 読み取りは別のモデルになる

第 3 章にも `commandservices` / `queryservices` の分離はありました。しかし読む先は同じテーブルで、分かれていたのは呼び出し口だけです。

イベント列は「予約 ID で 1 件引く」ことしかできません。「期限が近い順に 20 件」には答えられないので、答えるための表を別に作ります。

```java
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class CargoProjection {
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/projection/CargoProjection.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/projection/CargoProjection.java)

この 2 行のアノテーションが、あとの 2 つの節の主題になります。

## 緑のまま守られていなかったもの

第 4 章は「全テスト緑のまま守られていなかった 3 件」を扱いました。この実装にも同じ形があり、**しかも種類が違います**。第 4 章の 3 件はいずれも「検査が本番と違う条件で回っていた」ものでした。ここでの 3 件は、**検査する手段が原理的に無かった**ものです。

### 1. タグを付け忘れると、集約は空のまま復元される

```java
        @EventTag(key = "bookingId") String bookingId,
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/events/CargoBookedEvent.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/domain/model/events/CargoBookedEvent.java)

この 1 行が無いと何が起きるかを、ADR がそのまま書いています。

> **イベント側に `@EventTag(key = ...)` が要る。** `@EventSourced(tagKey = ...)` は集約側の宣言でしかなく、イベントで「どの項目がそのタグか」を言わないとタグが書かれない。
>
> 集約は**毎回空のまま復元される**。状態を見る守り（2 度目の受付を断る・状態遷移の検査）が丸ごと素通りし、**それでもテストは緑になる**。

引用元: [`docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`](../../source/java-4/docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)（決定 5・第 8 項。設計文書からの引用）

### 2. 集約の単体テストでは判別できない

同じ ADR の第 11 項が、1 と組み合わさる条件を書いています。

> **`AxonTestFixture` の `disableAxonServer()` では、タグによる復元が働かない。** `given().event(...)` も `given().command(...)` も、`when()` の集約からは見えない。
>
> 「復元した状態を見る守り」は集約の単体テストでは**判別できない**。外しても緑になる。実 Axon Server の統合テストに置く。

引用元: 同上（決定 5・第 11 項）

**8 と 11 は組み合わさると危険である**と ADR は明記し、「状態を見る守りを足したら、必ず実 Axon Server の統合テストで『壊して赤』を確かめる」と結論しています。

### 3. 復元演習は、件数も内容も一致したまま失敗する

3 件目は運用側です。Event Store の差分エクスポートと再投入を実機で確かめた結果が残っています。

> **集約の復元はできない。** エクスポートにタグ（DCB の label）が含まれない。タグ無しで投入したイベントは、集約のタグ（`shipperId`）で検索すると **0 件**になる。件数と内容は一致しているのに、`@EventSourced(tagKey)` の集約だけが読めない。

引用元: 同上（決定 5・第 7 項の詳細）

ADR はこれを「緑に見えるが何も見ていない種類の落とし穴」と呼びます。復元後にイベント件数と内容を突き合わせると完全に一致し、投影のリプレイも全件走査なので成功します。**壊れるのは復元後に初めてその集約へコマンドを送ったとき**です。

だから合格条件が変わります——復元演習には「復元した集約へコマンドを 1 本送って通ること」を必ず入れる、と。

## 守りを検査に落とす

第 4 章の主題は「規則を検査に落とす」ことでした。この実装は同じことを、**単体テストで判別できないものに対して**やっています。手段は静的な走査です。

### タグの検査

```java
    @Test
    @DisplayName("ADR-0001 決定 5 第 8 項: 集約が出すイベントはタグを宣言する")
    void appendedEventsDeclareTheTag() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Aggregate aggregate : eventSourcedAggregates()) {
            for (String eventName : aggregate.events()) {
                String eventBody = Files.readString(sourceOf(eventName), StandardCharsets.UTF_8);
                boolean declaresTag =
                        eventBody.contains("@EventTag(key = \"" + aggregate.tagKey() + "\")");
                if (!declaresTag) {
                    offenders.add(eventName + "（" + aggregate.file().getFileName()
                            + " の tagKey=" + aggregate.tagKey() + "）");
                }
            }
        }

        assertThat(offenders)
                .as("@EventTag(key) が無いイベントは、集約を空のまま復元させる。"
                        + "復元した状態を見る守りが丸ごと素通りし、集約の単体テストでは判別できない")
                .isEmpty();
    }
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java)

読むべきは**拾い方**です。

> **「タグを持つイベント」だけを探しません。** 集約が出しうるイベントを全部拾ってから、タグの有無を見ます。正しい形の行だけを拾う検査は、書いていないものを素通りさせます。

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java)

そして検査そのものが空振りしていないかを、同じクラスが見ます。

```java
    @Test
    @DisplayName("検査の対象が実在する（空振りしていない）")
    void actuallyInspectsAggregates() throws IOException {
        List<Aggregate> aggregates = eventSourcedAggregates();

        assertThat(aggregates)
                .as("@EventSourced(tagKey) の集約が 1 つも見つからないなら、"
                        + "上の検査は『守っている』ではなく『調べていない』")
                .isNotEmpty();
        assertThat(aggregates.stream().flatMap(a -> a.events().stream()).toList())
                .as("イベントを 1 つも拾えていないなら、appender.append の書き方が変わっている")
                .isNotEmpty();
    }
}
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/EventTagAccompaniesEventSourcedTest.java)

**この 2 本目が無い検査は、いつか静かに何も見なくなります。**`appender.append` の書き方が変わった日に、1 本目は「違反 0 件」で緑のままになるからです。

この検査が置かれた理由も、Javadoc が書いています——**同じ欠陥を 2 度作ったから**です。IT2 で `Cargo` について ADR に書いたのに、`Shipper` 側は残りました。「本文に書くだけでは、次の集約で同じことが起きる」。

### 形をそろえる検査

`EventSourcedServicesHaveTheSameShapeTest` は、Event Sourcing のサービスが同じ形で立ち上がることを見ます。見ているものは（IT16 時点で）10 本あり、コマンドハンドラが static でないこと、`@EventHandler` を持つパッケージが Processing Group として列挙されていること、投影を持つサービスに `ReplayIT` があることなどが含まれます。

設定側はこうなっています。

```yaml
axon:
  axonserver:
    servers: ${AXON_SERVER:localhost:8124}
  eventhandling:
    processors:
      # **退避先は Processor ごとに明示する**（ADR-0014）。`"[..default]"` は
      # この版では効かない（実測。DLQ が付かず、1 件で全部止まる形に戻る）。
      # 書き忘れを人の注意で防がないよう、EventSourcedServicesHaveTheSameShapeTest が
      # 2 つ見る：everyEventHandlerPackageIsEnumerated（@EventHandler を持つ
      # パッケージがここに載っているか）と everyEnumeratedProcessorHasADeadLetterQueue
      # （載っているものに dlq が付いているか）。
      # Processing Group はパッケージ名キーで指定する（@ProcessingGroup は Axon 5 に無い）。
      # 投影と Reaction Handler を別 Group にするため、パッケージを分けている。
      # 名前の正典は data-model.md「Processing Group とテーブルの対応」。
      "[com.example.cargotracker.booking.infrastructure.projection]":
        mode: pooled
        dlq:
          enabled: true
      "[com.example.cargotracker.booking.application.reaction]":
        mode: pooled
        dlq:
          enabled: true
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/resources/application.yml`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/resources/application.yml)

**コメントが検査の名前を名指ししています。**「書き忘れを人の注意で防がないよう」と書いたうえで、どのテストが何を見るかまで書いてある——設定と検査が互いを指しています。

### 決定が守られていることを、決定の数だけ確かめる

```java
    /**
     * 検査の節をまだ持たない ADR。<b>減らす方向にしか動かさない</b>。
     *
     * <p>IT9 で ADR-0008 を外した（決定 4 つは実装済みだったが、決定 3 の
     * 「ラベルの無い要素は例外にする」と決定 4 の読み口に検査が無かった）。</p>
     */
    private static final List<String> WITHOUT_CHECKS_YET = List.of(
            "0001-cqrs-es-with-axon-in-microservices.md",
            "0002-event-store-axon-server-and-postgresql-read-models.md",
            "0003-crypto-shredding-for-personal-data.md",
            "0004-demo-login-for-development.md",
            "0005-flyway-locations-per-service.md",
            "0006-role-authorization-at-the-gateway.md",
            "0007-route-search-cutoff.md",
            "0009-condition-review-is-not-a-state-transition.md");
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/AdrHasChecksTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/AdrHasChecksTest.java)

**ADR ごとに「検査」の節があるかを見る検査**です。動機は Javadoc にあります。

> **文章のまま残った決定は守られない。** ADR-0009 は「規則を書いただけで検査に落とさなかったため、7 IT のあいだ半分しか守られず違反が 5 本増えた」（IT7 の教訓）。

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/AdrHasChecksTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/AdrHasChecksTest.java)

**未対応の ADR を許可リストで持ち、減る方向にしか動かさない**形にしています。全部を今日直すことは求めず、新しいものには必ず要求する。第 3 章で見た「境界を検査で固定する」と同じ考え方が、ADR そのものに掛かっています。

### 文書が実装に追随していることまで検査する

もう 1 歩進んだものがあります。

```java
    @Test
    @DisplayName("ADR-0001 の記述が、実際の状態と一致している")
    void theAdrSaysWhetherTheCheckExists() throws IOException {
        String adr = Files.readString(
                backendRoot().getParent().getParent().getParent()
                        .resolve("docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md"),
                StandardCharsets.UTF_8);

        assertThat(adr.contains("**IT1 時点では未実装**"))
                .as("ReplayIT を書いたのに ADR に『未実装』が残っている。"
                        + "検査を書いたら ADR の記述も同じ変更で直す")
                .isFalse();
    }
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ReplayCheckAccompaniesReactionTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ReplayCheckAccompaniesReactionTest.java)

**ADR の本文を読んで、古い記述が残っていたら赤にします。**「検査を書いたら ADR の記述も同じ変更で直す」——本シリーズが M3（設計文書と実装の食い違い）と呼んできたものを、この実装は**赤にできる形**にしています。

ただし、できるのはここまでです。この検査が見るのは特定の 1 文字列で、文書全体の正しさではありません。**全部は機械で守れない**ことは、次の節が示します。

## 設計文書が実装より古かった箇所

この実装でも、設計文書は実装から離れます。**離れ方が第 1〜4 章と違う**ので、3 件を挙げます。

### 1. 設計が `@Saga` を前提に書かれていた

> 着手前の検証で、設計ドキュメントどうしが食い違っていることが分かった。
>
> - `architecture_backend.md` の「Saga パターン（Axon Saga）」節と `release_plan.md:208` は `@Saga` / `@StartSaga` / `@EndSaga` を前提に書かれていた
> - しかし **ADR-0001 決定 6 は「Axon 5 に Saga の API が無い」ことをスパイクで確認済み**で（5.0.0 / 5.1.0-RC2 / 5.3.1 のどの jar にも `Saga` / `SagaLifecycle` が 0 件）、`SagaIsStillAbsentTest` が「Saga が現れたら赤にする」検査として置かれていた

引用元: [`docs/adr/cargo-tracker/0010-reaction-handler-as-the-only-coordinator.md`](../../source/java-4/docs/adr/cargo-tracker/0010-reaction-handler-as-the-only-coordinator.md)（設計文書からの引用）

**注目すべきは、この食い違いが害を出さなかったことです。**同じ ADR が続けてこう書いています——「選び直す余地は無い。`@Saga` はそもそもコンパイルできないので、計画のとおりに着手していれば初日に破綻していた」。

第 2 章で見たドメインモデル図のずれ（6 か所）は、**読者を誤らせるが赤にはならない**種類でした。こちらは**コンパイラが止める**種類です。文書の食い違いにも、止まるものと止まらないものがあります。

### 2. 「ArchUnit で固定する」と書いた検査が実在しなかった

> `architecture_backend.md` は「契約に置くイベントは 11 本」と書き、「名簿は ArchUnit で固定する」とも書いていたが、**名簿の検査は実在しなかった**（IT16 の着手前に発見）。

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java)

**これは M3 の一段深い形です。**「設計が A と書き、実装が B である」ではなく、**「設計が『検査で守る』と書き、その検査が無かった」**。しかも同じ文書の本数（11 本）は、実装と 4 本ずれていました。

直したものが次です。

```java
    /**
     * 契約に置くイベント。<b>読む相手がいるものだけ</b>。
     *
     * <p>`TrackingClosedEvent` はここに<b>入らない</b>——閉じたことを読むサービスが
     * 無いので、trackingms の内部イベントのままにする（[ADR-0018] 決定 3）。</p>
     */
    private static final List<String> ROSTER = List.of(
            "CargoCancelledEvent",
            "CargoDeliveredEvent",
            "CargoDeliveryRevertedEvent",
            "CargoQuotedEvent",
            "CustomsStatusChangedEvent",
            "HandlingActivityRegisteredEvent",
            "HandlingActivityVoidedEvent",
            "PaymentRecordedEvent",
            "PaymentVoidedEvent",
            "ShipperRegisteredEvent",
            "TrackingInitializedEvent");
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java)

```java
    @Test
    @DisplayName("[ADR-0018] 契約イベントは名簿どおり（本数ではなく名前で固定する）")
    void contractEventsMatchTheRoster() throws IOException {
        List<String> actual;
        try (Stream<Path> files = Files.list(CONTRACT_EVENTS)) {
            actual = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .filter(name -> !"package-info.java".equals(name))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .sorted()
                    .toList();
        }

        assertThat(actual)
                .as("契約イベントを 1 つも読めていない（検査が空振りしている）")
                .isNotEmpty();
        assertThat(actual)
                .as("契約に置くイベントは名簿どおりにする。**足すなら、読む相手を"
                        + "決めてから名簿に足す**——契約は版を上げるのに Upcaster が要る")
                .containsExactlyElementsOf(ROSTER.stream().sorted().toList());
    }
}
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ContractEventRosterTest.java)

**本数ではなく名前で固定します。**本数だけを数えると、1 本足して 1 本消せば通る——**うっかり契約へ移したイベント**が素通りします。

第 4 章では契約を `testFixtures` に置いて「両側が同じ 1 つを読む」ことを守りました。ここではそれに加えて、**契約に置くこと自体を 1 本ずつの判断にしています**。契約は版を上げるのに Upcaster が要るからです。

### 3. 気づく手段の宛先が間違っていた

3 件目は、文書と実装のどちらが正しいかを実装側が決め直した例です。

> 設計（architecture_backend.md）は「追跡管理者の要確認一覧に写す」と書いていたが、**追跡管理者には打つ手が無い**——追跡番号を発行し直せるのは経路設計者だけである（ADR-0010 決定 3）。気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/application/reaction/BookingReactionHandler.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/application/reaction/BookingReactionHandler.java)

**設計が間違っていて実装が正しい**という向きの食い違いです。§3.1 に集めてきた例はほとんどが逆向き（設計が正典で実装が追随していない）でしたが、この向きも起こります。

## Saga が無いということ

Axon 5 に Saga の API はありません。「まだ移植されていない」ではなく、調べ切った結果です。

```java
    @Test
    @DisplayName("Axon に Saga のクラスが無い（現れたら ADR-0001 決定 6 を再評価する）")
    void axonStillHasNoSaga() {
        List<String> found = sagaClassesOnAxonClasspath();

        assertThat(found)
                .as("Axon に Saga が入った。ADR-0001 決定 6 の再評価の発動条件 1 に当たる。"
                        + "Reaction Handler の自前の状態管理と Saga の関連付け、Deadline の有無、"
                        + "移行の代金を比べて ADR を改訂すること。比べずにこの検査を消さない")
                .isEmpty();
    }
```

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/SagaIsStillAbsentTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/SagaIsStillAbsentTest.java)

**再評価の条件を検査にしています。**

> 採用中の Axon に Saga のクラスが現れたら**赤にする**。壊れたから赤なのではなく、「前提が変わったので ADR を読み直せ」という合図としての赤である。
>
> 発動条件を文章だけで持つと、版を上げたときに誰も読み返さない。

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/SagaIsStillAbsentTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/SagaIsStillAbsentTest.java)

代わりに置かれているのが Reaction Handler です。

```java
    /**
     * 1 段目。追跡番号が発行されたら、trackingms へ追跡開始を送る。
     *
     * <p><b>起票してから送る。</b> 送ってから起票すると、trackingms の応答のほうが
     * 先に届いて「行が無いのに 2 段目が来る」ことが起きる。</p>
     *
     * <p><b>値を落とさずに渡す。</b> 旅程は荷役（IT9）の材料になる。ここで落とすと、
     * 契約イベントに載せた意味が無くなる。</p>
     */
    @EventHandler
    public void on(TrackingNumberIssuedEvent event) {
        var state = processes.start(PROCESS_TYPE, event.bookingId(), STEP_INITIALIZE_TRACKING,
                TOTAL_STEPS, Map.of("trackingNumber", event.trackingNumber()));
        if (!state.isRunning()) {
            // 終わった連鎖に遅れて届いた。送り直すと追跡が作り直される。
            return;
        }

        try {
            commands.sendAndWait(new InitializeTrackingCommand(
                    event.trackingNumber(), event.bookingId(), event.shipperId(),
                    event.origin(), event.destination(), event.cargoType(), event.weightKg(),
                    event.legs().stream().map(leg -> new InitializeTrackingCommand.LegDto(
                            leg.voyageNumber(), leg.loadUnLocode(), leg.unloadUnLocode(),
                            leg.loadTime(), leg.unloadTime())).toList(),
                    event.issuedAt()));
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

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/application/reaction/BookingReactionHandler.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/application/reaction/BookingReactionHandler.java)

Saga の各機能は次のように置き換わります。

| Saga の機能 | 置き換え |
| :--- | :--- |
| `@StartSaga` | 1 段目が `process_state` に `RUNNING` の行を作る |
| `SagaLifecycle.associateWith()` | `process_state` の `process_type` + `process_id` |
| `@EndSaga` | 最後の段で `status = 'COMPLETED'`。**行は消さない** |
| Deadline | `RUNNING` かつ 24 時間より古い行を走査する運用ジョブ |
| Saga Store | `process_state`。**止まった位置がそのまま SQL で読める** |

引用元: [`docs/design/cargo-tracker/architecture_backend.md`](../../source/java-4/docs/design/cargo-tracker/architecture_backend.md)「Saga の各機能の置き換え」（設計文書からの引用）

**代替品として書かれていない**ことが重要です。ADR は「Axon 5 が勧めている形である」としたうえで、自分で持つほうがよい理由を 4 つ挙げ、**「Saga が戻ってきても、この 4 つを上回らない限り移らない」**と書いています。上の検査が赤くなった日に読み返すのは、この 4 つです。

第 4 章の「この実装にまだ無いもの」で、**Saga のオーケストレーションは扱わない（実装に無い）**と書きました。ここでは**フレームワークの側に無い**という別の理由で、同じ言葉が使えなくなっています。

## 投影とリプレイ

投影は `@EventHandler` を持つ普通の Spring コンポーネントです。規律が 2 つあります。

**1 つ目：投影はコマンドを送りません。**送るとリプレイのたびに副作用が再実行されます。これはレイヤー責務表にも「`infrastructure` にコマンドの送信を置かない」として書かれており、さらに `ReplayCheckAccompaniesReactionTest` が「Reaction Handler を入れるなら同じ変更で `ReplayIT` も入れる」ことを要求します。

> **ArchUnit の規則では代われない。** `CommandGateway` の利用箇所をパッケージで限定する規則はコンパイル時の依存しか見ておらず、「リプレイの実行中に呼ばれない」という動的な保証は別物である。

引用元: [`apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ReplayCheckAccompaniesReactionTest.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/test/java/com/example/cargotracker/shared/conventions/ReplayCheckAccompaniesReactionTest.java)

第 3・4 章で ArchUnit が引き受けていた保証に、**届かない範囲があること**を名指しした箇所です。しかも `ReplayIT` を書いた時点で実在の欠陥が出ています——読み直しのたびに要確認一覧が増えていました。

**2 つ目：処理の列の切り方が、被害の範囲を決めます。**

```java
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/projection/CargoProjection.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/projection/CargoProjection.java)

既定では列が全体で 1 本なので、1 件の毒で**無関係の予約のイベントまで退避されます**（IT12 のクラスタ E2E で 4 件のうち 3 件が巻き添えになったと Javadoc が記録しています）。退避先は順序を守るために「同じ列の後続」も退避するので、切り方がそのまま被害の範囲になります。

かといって予約より細かくは切れません。同じ予約の中では順序が要る（訂正は登録より後に効かなければならない）からです。**第 4 章のデッドレターが「1 件の毒で全部止まる」ことを防いだのと同じ問題が、粒度の選択として現れています。**

## Axon Server が引き受けないもの

設定は数行で済みます。しかし、その見た目が罠になります。

```java
/**
 * 起動時に Axon Server への接続と、context が DCB であることを検査する。
 *
 * <p>なぜ要るか。Axon Server に繋がらないとき、また context が DCB でないとき、
 * アプリケーションは<b>起動を止めずに無限に再接続を試み続ける</b>（IT1 スパイクで実測。
 * 2026.0.4 では {@code AXONIQ-1302 default: not found in any replication group}）。
 * 起動が成功してしまうので、投影が永久に進まないことに誰も気づかない。</p>
 *
 * <p>判定はログの検出ではなく context への問い合わせで行う。ログの文言は版で変わるうえ、
 * 「出なかった」ことを検査にできないため（[ADR-0001] 決定 5 の第 6 項目）。</p>
 */
public class AxonServerStartupCheck implements ApplicationRunner {
```

引用元: [`apps/cargo-tracker/backend/shared/src/main/java/com/example/cargotracker/shared/infrastructure/axon/AxonServerStartupCheck.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/main/java/com/example/cargotracker/shared/infrastructure/axon/AxonServerStartupCheck.java)

**繋がらなくても起動が成功します。**判定をログの検出ではなく context への問い合わせで行っているのは、ログの文言は版で変わるうえ「出なかった」ことを検査にできないためです。

自動設定に任せられない Bean もあります。

```java
/**
 * Axon の JDBC 系 Bean を明示的に組む。
 *
 * <p><b>なぜ手で組むか。</b> {@code TokenStore} は自動設定されず、無いと
 * {@code Could not find a mandatory TokenStore} で起動に失敗する（IT1 スパイク 0.2 で実測）。
 * {@code TransactionManager} は 1 つでなければならず、複数あると無音で
 * {@code NoTransactionManager} に落ちる。</p>
 *
 * <p>{@code SpringTransactionManager} を {@code ConnectionProvider} 付きで作るのは、
 * PooledStreamingEventProcessor の UnitOfWork に接続の実行者を bind するためで、
 * これが無いと {@code JdbcTokenStore} が
 * 「A connection executor must be present in the processing context」で失敗する
 * （take-4 ADR-0009 の実測）。</p>
 *
 * <p>DataSource を持たないサービス（gatewayms）では当たらないようにする。</p>
 */
@Configuration
@ConditionalOnClass(DataSource.class)
public class AxonJdbcConfiguration {
```

引用元: [`apps/cargo-tracker/backend/shared/src/main/java/com/example/cargotracker/shared/infrastructure/axon/AxonJdbcConfiguration.java`](../../source/java-4/apps/cargo-tracker/backend/shared/src/main/java/com/example/cargotracker/shared/infrastructure/axon/AxonJdbcConfiguration.java)

第 3 章では Spring Boot が黙って与えていたもの（`TransactionManager` が 1 つであること）が、ここでは**明示して組まないと無音で壊れます**。第 4 章の「保証が消えたのではなく、誰が引き受けるかが変わっている」が、フレームワークの内側でも起きています。

## 同期の越境は Query Bus になる

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
        return new RouteCandidates(
                response.candidates().stream()
                        .map(QueryBusRouteCandidateFinder::toCandidate)
                        .toList(),
                response.truncated());
    }
```

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/acl/QueryBusRouteCandidateFinder.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/acl/QueryBusRouteCandidateFinder.java)

ACL の責務は第 3・4 章と変わりません。変わったのは**落ちているときの見え方**です。

> **落ちているときに空リストを返さない。** 空にすると「候補が無い」と読まれ、経路設計者は条件を変え続ける。

引用元: [`apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/acl/QueryBusRouteCandidateFinder.java`](../../source/java-4/apps/cargo-tracker/backend/bookingms/src/main/java/com/example/cargotracker/booking/infrastructure/acl/QueryBusRouteCandidateFinder.java)

Query Bus を選んだ理由の 1 つがここにあります。REST の接続エラーと違い、**Axon Server は「誰も居ない」と明示的に答えます**（`NoHandlerForQueryException`）。第 4 章の REST 越しの ACL は、この区別を自分で作る必要がありました。

## この実装にまだ無いもの

**無いものを無いと書きます。**

| 無いもの | 状態 |
| :--- | :--- |
| Saga | **フレームワークに無い。** Reaction Handler + `process_state` で代替（ADR-0001 決定 6） |
| Deadline（タイムアウト起点の処理） | **フレームワークに無い。** 投影テーブルを走査する運用ジョブで代替 |
| Event Store の汎用エクスポート・復元 | **成立しない。** タグを読み返す口が Axon Server に無く、payload から組み立て直すのはアプリケーション固有の知識になる |
| authms の Event Sourcing | **適用しない判断。** 現在状態だけが業務に要り、履歴は監査ログで足りる（ADR-0001 決定 2） |
| GA の `axon-server-connector`（5.2 以降） | **未公開。** 5.1.0-RC2 を本番構成に採っている。公開された時点で昇格を検討し ADR を改訂する |

最後の 1 行は、第 4 章までには無かった種類の「無いもの」です。**採用したフレームワークの側が未完成である**ことを、そのまま構成に引き受けています。

## 前章までとの対比

| 観点 | 第 3 章（モノリス） | 第 4 章（EDA） | 本章（CQRS/ES） |
| :--- | :--- | :--- | :--- |
| 集約の保存 | 現在状態を UPDATE | 現在状態を UPDATE | イベントを追記し、再生して復元 |
| 不変条件の材料 | 集約 or DB の現在値 | 集約 or DB の現在値 | **集約だけ**（投影は結果整合） |
| イベントを改名する | 自由 | 契約の変更（両側に契約テスト） | **Upcaster が要る** |
| 調整役 | アプリケーション層のイベント購読 | メッセージハンドラ | Reaction Handler + `process_state` |
| 失敗の扱い | 捕まえて件数に記録する | 捕まえない（デッドレターへ） | 再試行 → 上限 → 補償と要確認一覧 |
| 守りが壊れたことの検知 | 集約の単体テストが赤 | 契約テスト・往復テストが赤 | **単体テストは緑のまま**（静的な検査と実 Axon Server の統合テストが要る） |
| ArchUnit で足りるか | 足りる | 足りる | **足りない**（リプレイ中の動的な保証は別物） |

**最下段の 2 行が、この章で新しく現れたものです。**上の 5 行は方式の違いですが、下の 2 行は**検知の手段が変わった**ことを示します。

「不変条件の材料」の行も効きます。投影は結果整合なので、「未決着のキャンセル申請は高々 1 件」のような判断を投影に尋ねると、追いついていないあいだは**申請できないと二重に申請できるの両方**が起こります。第 3 章なら「テーブルを 1 本引けば済む」ことが、ここでは集約が何を持つかの設計に跳ね返ります。

## トレードオフ

**得たもの。**

- 起きたことが全部残る。誤配も取り消しも、結果ではなく経緯として読める
- 読み取りモデルを業務の都合で何枚でも作れる。一覧・作業一覧・要確認一覧が同じ表を奪い合わない
- 配送経路が 1 種類になる。サービスは互いの URL を知らず、相手が居ないことが明示的に返る

**払った代金。**

- イベントが契約になる。改名も項目追加も、過去のイベントが読めるかどうかで縛られる
- 結果整合が業務判断に入り込む。不変条件の材料を投影に尋ねられない
- **検知の手段を自分で作る必要がある。**第 3 章なら集約の単体テストが落ちれば守りが壊れたと分かります。この方式では、守りが丸ごと外れていても単体テストは緑です

**採用しなかった選択肢が失わせたもの。**この実装は `Quotation` と `Voyage` も Event Sourcing にしています。履歴が業務として要る根拠は例外処理・誤配・通関にあり、見積と航海には無いのに、です。理由は記事の比較のためだと ADR が明記しています。**実務でこれをやる理由はありません。**

ただし同じ ADR が、降りる条件を先に置いています。

> **IT2 終了時点で実績ベロシティが計画の 70% 未満なら、`Quotation` と `Voyage` を状態保存（MyBatis の UPDATE）に落とす。**

引用元: [`docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md`](../../source/java-4/docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)（決定 2・設計文書からの引用）

「工数の問題が出たら考える」では検知できないので、数値で置く。**採用した方式を降りる条件を、採用するときに決めておく**——3 つの実装を通して、いちばん移植しやすい習慣でした。

## まとめ

- 参照元は 3 本目の別実装です。サービスの分割を第 4 章と揃えたのは意図的で、**差分が永続化と読み書きの分離だけになる**ようにしてあります
- イベントは保存のあとの通知ではなく、**保存そのもの**になります。消えたら業務の記録が消えるので、形を変えられません。値オブジェクトを載せない・パッケージ移動が無料でない、はすべてここから来ます
- **守りが丸ごと外れてなおテストが緑になる**種類の欠陥が 3 つ出ました。`@EventTag` の付け忘れ、単体テストで判別できないこと、復元演習が件数一致のまま失敗すること。いずれも第 4 章の 3 件（検査が本番と違う条件で回っていた）とは種類が違います
- 対処は**静的な検査**です。しかも「正しい形のものだけを探さない」「検査が空振りしていないことを確かめる」「ADR の記述が実態と一致していることまで見る」という形を取っています。**M3 を赤にできる形に落とした**例です
- 設計文書と実装の食い違いは、この実装でも起きました。ただし**止まるもの**（`@Saga` はコンパイルできない）と、**止まらないもの**（「ArchUnit で固定する」と書いた検査が実在しなかった）があります。後者のほうが危険です
- 前稿は同じ章を実装なしで書き、Axon の設計差分を構想として並べていました。**書けたのは構成要素の一覧まで**で、この章の中心——タグの付け忘れ、単体テストの限界、復元演習の落とし穴、検査の書き方——はどれも出てきません。**実装から書くとは、成功した設計ではなく失敗した経路が残ることです。**

次章では、ここまでの 3 つの実装を並べ、何を基準に選ぶかを整理します。
