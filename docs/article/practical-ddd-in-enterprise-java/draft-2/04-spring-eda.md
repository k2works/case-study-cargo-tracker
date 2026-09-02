---
type: Article
title: "第 4 章：プロセスを越えるイベント — マイクロサービス版の Cargo Tracker"
description: "BC をプロセスに分けたときにイベント駆動が何を要求するかを、RabbitMQ で連携する別実装から示す。契約・到達・冪等・コミット順序と、それらを守る検査。"
tags: [article, practical-ddd-in-enterprise-java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-02T01:40:00Z }
---

# 第 4 章：プロセスを越えるイベント — マイクロサービス版の Cargo Tracker

前章の末尾で、単一 JVM という前提が崩れる条件を 3 つ挙げました。その 1 つめ——**イベントの配送が JVM を越える必要が出たとき**——を、この章で扱います。

## 参照元が変わります

第 1〜3 章の参照元は [`docs/article/source/java-2/`](../../source/java-2)（モジュラーモノリス・単一 Gradle モジュール・`ApplicationEventPublisher`）でした。**この章だけ参照元が違います。**

| | 第 1〜3 章 | 本章 |
| :--- | :--- | :--- |
| 参照元 | [`source/java-2/`](../../source/java-2) | [`source/java-3/`](../../source/java-3) |
| 構成 | モジュラーモノリス（単一 Gradle モジュール） | マイクロサービス（8 サービス + 共有ライブラリ） |
| BC の実体 | トップレベルパッケージ | 独立した Spring Boot アプリケーション |
| データベース | 1 つ | Database per Service |
| BC 間の配送 | `ApplicationEventPublisher`（同一 JVM） | RabbitMQ（プロセス間） |
| 同期の越境 | ACL ポート（名簿に登録された 4 コマンド） | REST（ACL 経由） |

**別の実装であり、続きではありません。** 同じ業務（国際貨物輸送）を別の分割で作ったものです。前章までの設計判断がそのまま引き継がれているわけではないので、比較するときは「同じ業務をこう分けたらこうなった」として読んでください。

なぜ別実装を持ってきたかというと、**単一実装のまま EDA を語ると、書けることではなく書きたいことを書くことになる**からです。前稿（draft-1）の第 4 章は、モジュラーモノリスと同じコードを別の見出しで再説明することで章を成立させていました。同じことを繰り返さないために、この章はプロセスを越える配送が実在する実装だけを引きます。

## この章のゴール

1. BC をプロセスに分けたとき、イベント駆動が**何を新しく要求するか**を列挙できること
2. イベント契約の決定（何を・いつ・何を載せ・落ちたらどうするか）が、どのコードに落ちているかを辿れること
3. **「発行するコードを書いた」ことと「相手に届くこと」が別である**理由と、その差を埋める検査を説明できること

## サービスの分割

`settings.gradle` に並ぶのは 8 つのサービスと 1 つの共有ライブラリです。

```groovy
rootProject.name = 'cargo-tracker-microservices'

include 'shared'
include 'gatewayms'
include 'authms'
include 'bookingms'
include 'routingms'
include 'trackingms'
include 'handlingms'
include 'billingms'
include 'simulationms'
```

引用元: [`apps/backend/settings.gradle`](../../source/java-3/apps/backend/settings.gradle)

前章で見た `include` が 1 つも無い `settings.gradle` と対になる形です。**モジュラーモノリスでは「モジュラーモノリスのモジュールは Gradle のモジュールではない」と書きました。ここでは一致しています**——BC が独立したデプロイ単位である以上、ビルドの単位も分かれます。

データベースもサービスごとに分かれます。

| サービス | データベース名 | 主要テーブル | RDBMS |
| :--- | :--- | :--- | :--- |
| authms | auth_db | users, roles, user_roles | PostgreSQL 16.x |
| bookingms | booking_db | location, shipper, cargo, leg, estimate, route_candidate, cancellation_request | PostgreSQL 16.x |
| routingms | routing_db | voyage, carrier_movement | PostgreSQL 16.x |
| trackingms | tracking_db | tracking_activity, handling_event, tracking_exception_event | PostgreSQL 16.x |
| handlingms | handling_db | handling_activity, customs_declaration, customs_status_history | PostgreSQL 16.x |
| billingms | billing_db | invoice, discount_policy | PostgreSQL 16.x |
| simulationms | simulation_db | simulation_run, simulation_step_result | PostgreSQL 16.x |

引用元: [`docs/design/architecture_backend.md`](../../source/java-3/docs/design/architecture_backend.md)「Database per Service パターン」

**gatewayms にデータベースはありません。** 認可判定に要る情報はトークンに載っており、状態を持たないからです。

### 正典が数えているサービスは 7 つです

ADR-001 はサービスを 7 つと書いています。

> 国際貨物輸送管理システムのバックエンドを、DDD のバウンデッドコンテキストを境界とした 7 つのマイクロサービス（gatewayms / authms / bookingms / routingms / trackingms / handlingms / billingms）として構成する。

引用元: [`docs/adr/001-microservices-architecture.md`](../../source/java-3/docs/adr/001-microservices-architecture.md)

実装には `simulationms` があります。業務シナリオを自動実行する後から足したサービス（ADR-030・ADR-031）で、ADR-001 はそれを知らないまま「7 つ」と書き続けています。前章で見た**起動クラスの Javadoc が BC を「6 つ」と書いたまま追随していない**のと同じ形です。**構造は検査できても、構造の説明文は検査できません。**

## 配送手段の入れ替え

### 正典は Spring Cloud Stream、実装は素の AMQP

設計ドキュメントは、イベントの実装方針をこう書いています。

```java
// イベント発行（bookingms - infrastructure/messaging/。RabbitMQ を使う段階で追加する）
@Service
public class RabbitMQCargoEventPublisher {
    private final StreamBridge streamBridge;

    public void trackingNumberIssued(TrackingNumberIssued event) {
        // コミットのあとに送る（[ADR-022] 決定 6）。境目はユースケースが張る
        streamBridge.send("cargoBooking-out-0", event);
    }
}
```

引用元: [`docs/design/architecture_backend.md`](../../source/java-3/docs/design/architecture_backend.md)「Spring Cloud Stream + RabbitMQ の実装方針」

ADR-001 も同じことを書いています。

> **イベント駆動**: サービス間の非同期連携は RabbitMQ + Spring Cloud Stream

**実装に Spring Cloud Stream はありません。** 各サービスの `build.gradle` が持つのは `spring-boot-starter-amqp` だけです。

```groovy
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

引用元: [`apps/backend/bookingms/build.gradle`](../../source/java-3/apps/backend/bookingms/build.gradle)

`spring-cloud-stream` を含む依存はサービス全体で 0 件、`StreamBridge` を使うコードも 0 件です。実際に使われているのは `RabbitTemplate` と `@RabbitListener` という AMQP の素の API です。同じく ADR-001 が「Spring Cloud Contract でサービス間 API・イベントの契約を検証する」と書いたコンプライアンス項目も、実装には存在せず、**手書きの契約テスト**が置かれています（後述）。

**これは実装の欠陥ではありません。** 素の AMQP で足りており、抽象を 1 段増やす理由が無かった、というだけです。問題は、その判断が ADR にも設計ドキュメントにも反映されていないことです。前章で扱った M2——**書いたことと、していることは別**——が、章をまたいで同じ形で現れます。

### 発行はここだけが知っている

```java
/**
 * 予約のイベントを RabbitMQ へ流す（[ADR-022]）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る。</strong>ドメインもユースケースも
 * {@link CargoEventNotifier} という「何を頼むか」しか知らない
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 */
public class RabbitCargoEventNotifier implements CargoEventNotifier {

    private final RabbitTemplate rabbitTemplate;

    public RabbitCargoEventNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void trackingNumberIssued(TrackingNumberIssued event) {
        afterCommit(() -> rabbitTemplate.convertAndSend(CargoEventChannels.EXCHANGE,
                CargoEventChannels.TRACKING_NUMBER_ISSUED, event));
    }
```

引用元: [`apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/acl/RabbitCargoEventNotifier.java`](../../source/java-3/apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/acl/RabbitCargoEventNotifier.java)

**構造は前章と同じです。** ユースケースは `CargoEventNotifier` という出力ポートしか知らず、AMQP か Kafka かはインフラ層の実装が引き受けます。**変わったのは実装クラスの中身だけ**で、ドメインもアプリケーションサービスも配送手段の入れ替えを見ていません。

これがヘキサゴナルの配置が返してくれるものです。前章で「Spring を外側に置く」と書いた配置は、**プロセス境界が入ったときに初めて代金を回収します**。

受信側も対称です。

```java
@Component
public class TrackingNumberIssuedListener {

    private final StartTrackingUseCase startTracking;

    public TrackingNumberIssuedListener(StartTrackingUseCase startTracking) {
        this.startTracking = startTracking;
    }

    @RabbitListener(queues = TrackingEventChannels.QUEUE)
    public void onTrackingNumberIssued(TrackingNumberIssuedMessage message) {
        startTracking.start(message.trackingNumber(), message.bookingId(),
                message.originUnLocode(), message.destinationUnLocode(),
                message.arrivalDeadline(), message.estimatedArrival());
    }
}
```

引用元: [`apps/backend/trackingms/src/main/java/com/example/trackingms/interfaces/events/TrackingNumberIssuedListener.java`](../../source/java-3/apps/backend/trackingms/src/main/java/com/example/trackingms/interfaces/events/TrackingNumberIssuedListener.java)

**このクラスに例外の握りつぶしはありません。** 意図的です。

> **例外を握りつぶさない。** 握りつぶすと、受け取れなかったイベントが正常に処理されたことになり、デッドレターにも届かない。追跡が作られないまま、どこにも異常が残らない状態になる（[ADR-022] 決定 4）。

`try-catch` を書かないことが決定である、という形は珍しくありません。**書かなかったことは差分に現れない**ので、こうしてコメントで理由を残さないと、次の担当者が「例外処理が漏れている」と読んで親切に足します。

## イベント契約の 7 つの決定

プロセスを越えると、決めなければならないことが増えます。ADR-022 はそれを 7 つに整理しています。

> 決めることが 6 つある。イベントは**受け手が読んだあと取り消せない**ため、あとで直すのが REST より高くつく。

引用元: [`docs/adr/022-domain-event-contract.md`](../../source/java-3/docs/adr/022-domain-event-contract.md)

以下、決定ごとに実装を辿ります。

### 決定 1: 何を、いつ発行するか

設計の一覧には `CargoBookedEvent`（予約したときに発行し、trackingms が追跡番号を採番する）がありました。ADR-022 はこれを**廃止**し、`TrackingNumberIssuedEvent` に置き換えます。

理由は、採番の位置が別の ADR で先に決まっていたからです。

> trackingms が要るのは「**この番号で追跡を始めよ**」であり、それが起きるのは追跡番号を発行したとき（US14）である。予約したときではない。予約の時点では番号が無く、追跡を作れない。

**イベントの名前は、送り手の都合ではなく受け手が必要とする瞬間で決まります。** 「予約された」は送り手にとっての事実ですが、受け手はその時点では何もできません。

設計の一覧には、廃止した行が消されずに残っています。

| イベント | 発行元サービス | 処理先サービス | 内容 |
| :--- | :--- | :--- | :--- |
| ~~`CargoBookedEvent`~~ | — | — | **廃止**（ADR-022 決定 1） |
| `TrackingNumberIssuedEvent` | bookingms | trackingms | 追跡番号を発行したときに発行し、trackingms が追跡を作る |
| `CargoCancelledEvent` | bookingms | trackingms | キャンセル確定 → 追跡へお知らせを記録。**理由は載せない** |
| `HandlingActivityRegisteredEvent` | handlingms | trackingms, bookingms | 荷役作業登録 → 輸送ステータス同期 |
| `CustomsStatusChangedEvent` | handlingms | trackingms | 通関状態変更。HELD なら例外「税関保留」を自動起票する |
| `CargoRoutedEvent` | bookingms | trackingms | **発行しない**（追跡を作るのに旅程は要らない） |
| `CargoDeliveredEvent` | trackingms | billingms | **実装しない**（起点は経理担当者の操作であり、自動起票は受入基準に無い） |
| `InvoiceCreatedEvent` | billingms | （通知システム） | **実装しない**（購読する相手がいない） |

引用元: [`docs/design/architecture_backend.md`](../../source/java-3/docs/design/architecture_backend.md)「ドメインイベント一覧」

**発行しないものが 3 つ、廃止が 1 つ、実際に流れているのは 4 つです。** 一覧の半分が「出さない」ことの記録になっています。

理由は一貫しています——**読む側の無い配線を先に敷かない**。購読者のいないイベントを出すと、契約だけが増えて誰も守りません。項目を足すたびに「これは誰が読むのか」を確かめる相手がいないので、そのまま腐ります。

### 決定 2: ペイロードに何を載せるか

> 載せるのは `trackingNumber` / `bookingId` / `originUnLocode` / `destinationUnLocode` / `arrivalDeadline` / `occurredAt` である。
>
> **ID だけにしない。** ID だけだと trackingms が bookingms へ問い合わせることになり、非同期にした意味が消える（同期の依存が戻り、bookingms が落ちていると追跡が作れない）。
>
> **予約の全部も載せない。** 載せるほど受け手が Booking の言葉に縛られ、こちらの項目を変えるたびに向こうが壊れる。載せるのは「追跡を始めるのに要るもの」だけである。

**両端が失敗です。** ID だけなら非同期にした意味が消え、全部載せれば受け手が送り手の語彙に縛られます。基準は「受け手が自分の集約を作るのに要るもの」であり、これは受け手の側からしか決められません。

### 決定 3: 後方互換は「項目の追加だけ」

> 項目の削除・改名・意味の変更はしない。受け手は**知らない項目を無視する**。
>
> 破壊的な変更が要るときは、**新しいイベント型を足して両方を出し**、受け手が移ってから古いほうを止める。イベントは受け手が読んだあと取り消せないので、REST のように「両側を同じ変更で直す」ことができない。

REST との違いがここに出ます。REST は要求と応答が対になっているので、両側を同じ変更で直せます。イベントは**送った時点で相手の手を離れており**、両側同時の変更という選択肢がありません。

### 決定 4: 受け取れなかったイベントは捨てず、デッドレターへ

再試行は設定に落ちています。

```yaml
    listener:
      simple:
        # 受け取れなかったイベントは捨てず、3 回試してからデッドレターへ送る（ADR-022 決定 4）。
        # default-requeue-rejected を false にしないと、失敗したメッセージが同じキューへ
        # 戻り続け、デッドレターに届かないまま無限に再処理される
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1s
          multiplier: 2
```

引用元: [`apps/backend/trackingms/src/main/resources/application.yml`](../../source/java-3/apps/backend/trackingms/src/main/resources/application.yml)

**`default-requeue-rejected: false` が無いと、デッドレターの設定は書いてあっても働きません。** 失敗したメッセージが同じキューに戻り続け、永久に再処理されるだけです。「設定を書いた」ことと「そこへ届く」ことが別である、という形がここにも出ています。

#### デッドレターが守らない壊れ方

デッドレターが受け止めるのは「受け取ったが処理できなかった」だけです。**ルーティングキーの綴りが違う場合や、購読側がまだ配線されていない場合、イベントはどのキューにも入らないまま消えます。** しかも発行側は成功を返すので、どこにも異常が残りません。

この実装はそこに 2 つめの受け皿を置いています。

```java
    /**
     * 貨物イベントの交換機。
     *
     * <p><strong>行き場のないイベントを予備の交換機へ逃がす</strong>（[ADR-022] 決定 4）。
     * ルーティングキーの綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らず
     * 黙って消え、発行側は成功を返す。デッドレターはこの形を守らない。
     */
    @Bean
    public TopicExchange cargoEventExchange() {
        return new TopicExchange(CargoEventChannels.EXCHANGE, true, false,
                Map.of("alternate-exchange", CargoEventChannels.UNROUTABLE_EXCHANGE));
    }
```

引用元: [`apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/config/BookingConfig.java`](../../source/java-3/apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/config/BookingConfig.java)

AMQP の `alternate-exchange` は、どのキューにも結びつかなかったメッセージの行き先です。**デッドレターと予備の交換機は守る範囲が違う**ので、両方が要ります。

| 壊れ方 | 受け止めるもの |
| :--- | :--- |
| 受け取ったが処理中に例外 | デッドレターキュー（3 回再試行後） |
| ルーティングキーの綴り違い・購読側の配線漏れ | 予備の交換機（alternate-exchange） |
| コミット後・発行前にプロセスが落ちた | **どちらも受け止めない**（決定 6 の残った窓） |

### 決定 5: 順序保証は前提にしない。受け手は冪等にする

> IT6 は 1 種類しか出さないので順序は問題にならない。だが前提にすると、あとで複数のイベントを足したときに黙って崩れる。

**「いまは問題にならない」ことを前提にしない**という判断です。この判断が正しかったことは、あとで実際に証明されます。契約クラスのコメントに残っています。

> **推定到着日はここに載せる**（[ADR-024] 決定 4）。別のイベントで送ると、2 つのイベントが別々のキューを通るため**順序が保証されない**——追跡の作成より先に届いた到着日は、引く相手が無く捨てられる。**kind の統合環境で実際に起きた**。

引用元: [`apps/backend/shared/src/testFixtures/java/com/example/shared/contract/TrackingNumberIssuedContract.java`](../../source/java-3/apps/backend/shared/src/testFixtures/java/com/example/shared/contract/TrackingNumberIssuedContract.java)

**順序に依存する 2 つの事実は、1 つのイベントに載せるしかありません。** 分けた時点で、到着の順序はブローカーの都合になります。

### 決定 6: コミットしたあとに発行する

```java
    /**
     * コミットしたあとに送る（[ADR-022] 決定 6）。
     *
     * <p>コミット前に出すと、<strong>ロールバックした予約のイベントが飛ぶ</strong>。
     * 存在しない予約の追跡ができ、荷主は追えるのに貨物が無い状態になる。
     *
     * <p><strong>ここで決めるのは、トランザクションの境目がインフラの関心だからである。</strong>
     * ユースケースに「コミット後に呼べ」と作法を課すと、入口が増えた数だけ破られる。
     *
     * <p>トランザクションの外から呼ばれたときはそのまま送る。バッチや管理操作のように
     * トランザクションを張らない経路があり、そこで黙って送られないほうが危ない。
     */
    private void afterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
```

引用元: [`apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/acl/RabbitCargoEventNotifier.java`](../../source/java-3/apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/acl/RabbitCargoEventNotifier.java)

**この機構には前提があります。** 呼ばれた時点でトランザクションが生きていることです。生きていなければ `isSynchronizationActive()` が false になり、そのまま送られます——つまり**機構が丸ごと素通りします**。

ADR-022 はその前提を注記に書いています。

> **境目はユースケースに置く。** 置かないと、保存のトランザクションは `save` が戻った時点でコミット済みになり、発行の時点で同期が解除されている。つまり**この機構が一度も働かない**——結果の順序が正しいのは「たまたま save のあとに呼んでいる」からであり、決定が守られている根拠にはならない（IT6 のクローズレビューで見つけた）。

### 決定 7: 採番は発行側が行う

> イベントは**採番済みの番号を運ぶ**のであって、採番を依頼するのではない。

決定 1 の裏返しです。採番を受け手に任せると、番号の形式を守る場所が 2 つになります。

## 契約をどこに置くか

送り手と受け手が別のプロセスにいる以上、**交換機の名前もルーティングキーも項目の名簿も、両側に写しとして存在します。** 写しがずれると「送っているのに届かない」形で壊れ、しかも送り手はエラーになりません。

この実装は、写しを 1 つに寄せました。

```java
/**
 * 追跡番号を発行したことのイベント契約（[ADR-022]）。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>これまでは項目の名簿と交換機・ルーティングキーが
 * プロデューサ側（bookingms）とコンシューマ側（trackingms）の両方に写しとして置かれていた。
 * 写しがずれると「送っているのに届かない」形で壊れ、しかも<strong>送り手はエラーにならない</strong>。
 * 両側の写しを同時に直すことに頼るのは、直し忘れを検出できない。
 *
 * <p>ここに置くのは<strong>契約であって実装ではない</strong>。イベントの DTO は BC ごとに
 * 持つ（相手の型を持ち込まない）。共有するのは「両者が合意した名前と項目」だけである。
 *
 * <p>testFixtures に置くのは、これがテストの道具だからである。本番のコードはこれを読まない
 * ——読ませると、共有カーネルに業務の契約が入り込み、片方の変更が両サービスの再デプロイに
 * なる（{@code sharedKernelScopeRule} が守っている境目）。
 */
public final class TrackingNumberIssuedContract {
```

引用元: [`apps/backend/shared/src/testFixtures/java/com/example/shared/contract/TrackingNumberIssuedContract.java`](../../source/java-3/apps/backend/shared/src/testFixtures/java/com/example/shared/contract/TrackingNumberIssuedContract.java)

**共有するのは契約であって型ではありません。** イベントの DTO は各サービスが自分で持ちます。共有カーネルに置くのは「両者が合意した名前と項目」だけで、しかも `testFixtures`——本番のコードから読めない場所——に置きます。読ませると、片方の変更が両サービスの再デプロイになり、独立したデプロイ単位という前提が消えます。

契約が持つのは 4 つです。

| 要素 | 値 |
| :--- | :--- |
| 交換機 | `cargoBookingChannel` |
| ルーティングキー | `cargo.tracking-number-issued` |
| 項目（順序も含めて契約） | `trackingNumber` / `bookingId` / `originUnLocode` / `destinationUnLocode` / `arrivalDeadline` / `estimatedArrival` / `occurredAt` |
| プロデューサの型名 | `com.example.bookingms.application.internal.outboundservices.acl.TrackingNumberIssued` |

最後の 1 つが、この構成の核心です。

> この名前は**コンシューマのクラスパスに存在しない**。それでも読めることが「相手の型を共有しない」という判断の根拠であり、コンシューマ側の契約テストが確かめる。

JSON の変換器はメッセージヘッダ `__TypeId__` に送り手の型名を載せます。受け手のクラスパスにその型はありません。**それでも読めることが、型を共有しない設計が成り立つ根拠**です。読めなければ全イベントがデッドレターへ落ち、送り手はエラーになりません。

### 交換機そのものも契約になります

```java
/**
 * 交換機そのものの契約（[ADR-022] 決定 4）。
 *
 * <p><strong>名前が一致しているだけでは足りない。</strong>交換機は、耐久性・自動削除・引数まで
 * 含めて同じでなければ再宣言できない。食い違うと、後から接続したほうが
 * {@code PRECONDITION_FAILED - inequivalent arg} で落ちる。しかも既存の交換機は
 * <strong>宣言し直せない</strong>ため、落ちたサービスは後続のキュー宣言まで止まる。
 *
 * <p>IT7 の kind 統合で実際に踏んだ。Testcontainers は毎回まっさらな交換機を作るので、
 * <strong>この壊れ方はテストでは出ない</strong>。守っているのが各サービスのコメントだけ
 * だったため、契約として置き直す。
 */
public final class EventExchangeContract {
```

引用元: [`apps/backend/shared/src/testFixtures/java/com/example/shared/contract/EventExchangeContract.java`](../../source/java-3/apps/backend/shared/src/testFixtures/java/com/example/shared/contract/EventExchangeContract.java)

**Testcontainers では出ない壊れ方があります。** テストは毎回まっさらなブローカーを立てるので、交換機の引数が食い違っていても最初の宣言が通ります。既存環境で初めて `PRECONDITION_FAILED` が出て、しかも**後続のキュー宣言まで止まります**。

## 届くことをどう確かめるか

### 往復テスト

```java
/**
 * イベントが<strong>実際に届く</strong>ことを実 RabbitMQ で確かめる、往復テストの土台。
 *
 * <p>「発行するコードを書いた」「購読するコードを書いた」ことと、<strong>相手に届くこと</strong>は
 * 別である。交換機の名前・ルーティングキー・キューの結びつけ・変換器のどれか 1 つがずれると、
 * <strong>送り手はエラーにならないまま届かない</strong>。
 *
 * <p><strong>契約ごとにテストを分ける。</strong>1 つのクラスに 2 契約を入れていたが、
 * US17 で 3 契約目が入る。契約が増えるたびに同じクラスが伸びると、どの契約の何を
 * 確かめているのかが読めなくなる。土台だけを共有し、契約ごとの取り決めは各テストが持つ。
 */
abstract class EventRoundTripTestBase extends TrackingIntegrationTestBase {
```

引用元: [`apps/backend/trackingms/src/test/java/com/example/trackingms/EventRoundTripTestBase.java`](../../source/java-3/apps/backend/trackingms/src/test/java/com/example/trackingms/EventRoundTripTestBase.java)

`TrackingNumberIssuedEvent` の往復で確かめているのは 5 つです。

| 見ていること | なぜ要るか |
| :--- | :--- |
| 発行されたイベントが届き、追跡の記録が残る | 配線のどれか 1 つがずれても送り手はエラーにならない |
| 同じイベントが 2 回届いても追跡は 1 件 | 再試行がある以上、二重配送は起こる（決定 5） |
| 保存先を同じ追跡番号で 2 回呼んでも落ちず 1 件のまま | 冪等をリスナーではなく保存先で担保していること |
| 処理できなかったイベントはデッドレターに残る | 「設定を書いた」ことと「届く」ことは別（決定 4） |
| どのキューにも結びつかないイベントは予備の行き先に残る | デッドレターが守らない壊れ方（決定 4） |

デッドレターの検査は、**わざと処理できないイベントを流します**。

```java
    @Test
    @DisplayName("処理できなかったイベントはデッドレターに残る")
    void movesUnprocessableEventsToTheDeadLetterQueue() {
        startListening();
        long before = deadLetterCount();

        // 地点マスタに無い港。握りつぶすと、出発地の分からない追跡ができる
        publish(payload("TRK-20260822-9003", "BKG-2026000002", "XXXXX"));

        awaitAssert(() -> assertThat(deadLetterCount())
                .as("処理できなかったイベントがどこにも残っていない")
                .isGreaterThan(before));

        assertThat(activities.findByTrackingNumber(TrackingNumber.of("TRK-20260822-9003")))
                .as("処理できなかったのに追跡を作っている")
                .isEmpty();
    }
```

引用元: [`apps/backend/trackingms/src/test/java/com/example/trackingms/TrackingNumberIssuedRoundTripTest.java`](../../source/java-3/apps/backend/trackingms/src/test/java/com/example/trackingms/TrackingNumberIssuedRoundTripTest.java)

**アサートが 2 本あることに意味があります。** デッドレターに届いたことだけを見ると、「デッドレターにも入れつつ追跡も作ってしまう」実装が緑になります。

### 送る形を本番と揃える

往復テストには、一度失敗した設計があります。

```java
    /**
     * プロデューサが実際に送る形で流す。
     *
     * <p><strong>相手の型名を手で載せる。</strong>こちらの受け皿クラスを渡して送ると、
     * {@code __TypeId__} には<strong>こちらのクラスパスに必ず存在する名前</strong>が載る。
     * 本番で載るのは相手の型名であり、この違いはワイヤ上でしか出ない。
     * <strong>相手の都合が伝わるか</strong>——往復テストが唯一確かめられるはずのものが、
     * それでは抜け落ちる（IT6 のクローズレビュー）。
     */
    protected void send(String exchange, String routingKey, String producerTypeId, String json) {
```

引用元: [`apps/backend/trackingms/src/test/java/com/example/trackingms/EventRoundTripTestBase.java`](../../source/java-3/apps/backend/trackingms/src/test/java/com/example/trackingms/EventRoundTripTestBase.java)

**受け皿クラスをそのまま送ると、テストは緑になります。** ヘッダに載るのは自分のクラスパスにある型名だからです。本番で載るのは送り手の型名であり、その違いはワイヤ上にしか現れません。往復テストが唯一確かめられるはずのもの——**相手の都合が伝わるか**——が、それでは抜け落ちます。

### プロデューサ側にも契約テストを置く

```java
/**
 * 追跡番号のイベント契約（<strong>プロデューサ側</strong>・[ADR-022]）。
 *
 * <p>コンシューマ（trackingms）が読む<strong>項目名・型・交換機とルーティングキー</strong>を、
 * こちら側でも固定する。
 *
 * <p><strong>片側だけの検査では守れない。</strong>コンシューマのテストは自分で組み立てた
 * メッセージに対して緑になるため、プロデューサが項目名を変えても気づけない。ここが対に
 * なって初めて、「送っているのに届かない」を捕まえられる。<strong>しかも送り手は
 * エラーにならない</strong>——ずれても誰も気づかないのが、REST より始末が悪いところである。
 *
 * <p>名簿は手で書かず、<strong>DTO の要素から導いて写しと突き合わせる</strong>
 * （REST 契約で IT6 タスク 0.3 に入れた形）。
 */
@DisplayName("追跡番号のイベント契約（プロデューサ側）")
class TrackingNumberIssuedContractTest {
```

引用元: [`apps/backend/bookingms/src/test/java/com/example/bookingms/infrastructure/acl/TrackingNumberIssuedContractTest.java`](../../source/java-3/apps/backend/bookingms/src/test/java/com/example/bookingms/infrastructure/acl/TrackingNumberIssuedContractTest.java)

**名簿を手で書かない**という点が効きます。手書きの名簿は、こちらが項目を足しても赤になりません。足した項目をコンシューマが読めているかは誰も確かめておらず、実物でだけ null になります。この実装はレコードの要素から名簿を導き、共有契約の `FIELDS` と突き合わせます。

さらに、**本番と同じ変換器を通します**。

> テストが自前の ObjectMapper で組み立てると、契約テストだけが通り、本物が送る形は違うという状態を素通りさせる。実際このテストを書いたとき、テスト側の設定では日付が配列（`[2030,9,20]`）になった。本番の変換器はそうならないが、**それはテスト側の設定からは分からない**——だから本番の変換器を通す。

## 規則を検査に落とす

### メッセージ基盤に触れる場所を限る

```java
    public static ArchRule eventPublishingOnlyInMessagingInfrastructureRule() {
        return classes()
                .should(new ArchCondition<JavaClass>(
                        "メッセージ基盤に触るのは境界パッケージだけ（ADR-022）") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        // 合成ルート（config）は両側を知ってよい。ポートと実装を束ねる場所であり、
                        // ここを塞ぐと Bean の宣言ができない
                        if (javaClass.getPackageName().contains(".infrastructure.messaging")
                                || javaClass.getPackageName().contains(".infrastructure.acl")
                                || javaClass.getPackageName().contains(".interfaces.events")
                                || javaClass.getPackageName().endsWith(".config")) {
                            return;
                        }
```

引用元: [`apps/backend/shared/src/testFixtures/java/com/example/shared/architecture/HexagonalArchitectureRules.java`](../../source/java-3/apps/backend/shared/src/testFixtures/java/com/example/shared/architecture/HexagonalArchitectureRules.java)

この規則には**由来があります**。以前は「誰も触っていない」ことを検査していました。発行を足すときに丸ごと消さず、**触ってよい場所に絞った**のです。

> [ADR-019] 決定 3 は「IT5 では発行しない」と決め、この検査は**誰も触っていない**ことを見ていた。IT6 で発行を足したので、**丸ごと消さずに絞る**。消すと以後の発行が無検査になり、ドメイン層やコントローラから直接発行しても気づけない。

**検査を消すのが最も安い選択肢である瞬間があります。** 規則の前提が変わったときです。そこで消すと、以後は無検査になります。

もう 1 つ、この規則には掛け漏れの履歴も残っています。

> `eventPublishingOnlyInMessagingInfrastructureRule` が bookingms だけに適用され、AMQP に最も広く触っている trackingms が無検査だったのがその形である。
>
> ここに足せば、その瞬間に全サービスへ掛かる。サービス側は「自分は誰か」（サービス名とトークンの扱い）だけを申告する。

**検査が存在することと、対象すべてに掛かっていることは別です。** サービスごとに検査クラスを書く構成では、後から作ったサービスが静かに漏れます。

### 「しない」という決定も検査に落とす

```java
/**
 * 「購読しない」という決定を検査に落とす。
 *
 * <p><strong>否定の決定も検査に落とす。</strong>落とさなければ、あとから購読を足したとき
 * 「決定を意図的に覆したのか、写し漏れたのか」が区別できない。決定が 2 つで検査が 1 つ
 * なら、片方は文章のままである。
 */
public final class EventSubscriptionRules {
```

引用元: [`apps/backend/shared/src/testFixtures/java/com/example/shared/architecture/EventSubscriptionRules.java`](../../source/java-3/apps/backend/shared/src/testFixtures/java/com/example/shared/architecture/EventSubscriptionRules.java)

発行側にも対になる検査があります。

```java
    /** メッセージ基盤へ送り出しているか。型名でも名前でもなく、送信のメソッドで見る。 */
    private static boolean publishes(JavaMethod method) {
        return method.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getPackageName()
                                .startsWith("org.springframework.amqp")
                        && (call.getName().startsWith("convertAndSend")
                                || call.getName().equals("send")));
    }
```

引用元: [`apps/backend/trackingms/src/test/java/com/example/trackingms/TrackingPublishesNothingTest.java`](../../source/java-3/apps/backend/trackingms/src/test/java/com/example/trackingms/TrackingPublishesNothingTest.java)

**判定を呼び出し箇所で行う**のが要点です。

> ポートの形ではなく**発行の呼び出し箇所を数える**——ポートに足さずにメッセージ基盤を直接呼べば、ポートを見るだけの検査は迂回できる。

第 3 章で扱った「規則は検査に落とさなければ守られない」が、ここでは**否定形の規則にまで及んでいます**。「出さない」「購読しない」は書いただけでは守られず、しかも破られたときに事故として現れません。静かに配線が増えるだけです。

## 全テスト緑のまま、守られていなかったもの

ADR-022 には後日談が付いています。**3 件とも、全テストが緑の状態で見つかりました。**

> 1. **決定 6 の機構が本番で一度も働いていなかった。** `@Transactional` はリポジトリの `save` にしか無く、発行の時点では同期が解除されていた。境目をユースケースへ引き上げ、「発行の時点でトランザクションが生きている」ことを実 DB のテストで固定した（外すと赤になることを確認）。
>
> 2. **往復テストがプロデューサの送る形を流していなかった。** 受け皿クラスをそのまま送っていたため、`__TypeId__` には**受け手のクラスパスに必ず存在する名前**が載っていた。本番で載るのは bookingms の型名であり、この違いはワイヤ上でしか出ない。
>
> 3. **コンシューマ側の「知らない項目を無視する」が、テスト自身が寛容に設定した ObjectMapper に対する検査だった。** 本番の変換器を通す形に直した。プロデューサ側の契約テストが戒めていた罠を、コンシューマ側だけが踏んでいた。

3 件に共通する形があります。**検査が、本番とは違う条件で回っていた**ことです。

| # | 検査が見ていたもの | 本番で起きていること |
| :--- | :--- | :--- |
| 1 | 発行の呼び出し順序 | トランザクションが張られておらず、機構が素通り |
| 2 | 自分の型で組み立てたメッセージ | 相手の型名が載ったメッセージ |
| 3 | テストが設定した寛容な変換器 | 本番の変換器 |

いずれも「テストを書いた」ことは事実です。**書いたテストが本番の条件を再現していなかった**だけです。3 番目にいたっては、プロデューサ側の契約テストが同じ罠を明示的に戒めていました。**片側で学んだことが、対になるもう片側に写っていませんでした。**

## この実装にまだ無いもの

第 3 章と同じく、**無いものを無いと書きます。**

**Transactional Outbox はありません。** コミットは成功したが発行に失敗する窓が残っています。ADR-022 はそれを明記しています。

> **逆に、コミットは成功したが発行に失敗する窓は残る。** Transactional Outbox で塞げるが、IT6 では入れない。**残っていることをここに明記する**——書かないと、塞いだつもりで運用に入る。塞ぐのは、発行が増えて取りこぼしが見えるようになってからでよい。

**Event Sourcing もイベントストアもありません。** 集約は現在状態を直接 UPDATE します。ADR-001 は代替案として検討したうえで見送っています。

> **イベントソーシング（Billing）**: 監査要件は満たしやすいが、初期フェーズには複雑すぎる。`Money` 値オブジェクト + 監査ログで代替し、必要になった時点で再検討する

**デッドレターに溜まったイベントを戻す運用も自動化されていません。** ADR-022 のネガティブに「IT6 では手動（運用手順書に載せる）」と書かれたままです。

## モジュラーモノリスとの対比

第 3 章の実装（`source/java-2`）と本章の実装（`source/java-3`）を、イベントの扱いだけで並べます。

| | モジュラーモノリス | マイクロサービス |
| :--- | :--- | :--- |
| 配送 | `ApplicationEventPublisher`（同一 JVM） | RabbitMQ トピック交換機 |
| 契約 | 型そのもの（同じクラスを両側が参照） | 名前と項目の合意（型は共有しない） |
| 到達の確認 | 不要（メソッド呼び出し） | 往復テスト（実ブローカー） |
| 失敗の行き先 | 例外がその場で伝播 | デッドレター + 予備の交換機 |
| 二重配送 | 起きない | 起こる前提で冪等にする |
| 順序 | 発行順 | **保証しない**。順序に依存する事実は同じイベントに載せる |
| コミットとの前後 | `@TransactionalEventListener` | `TransactionSynchronizationManager` |
| 取りこぼし | カウンタに記録 | 3 回再試行 → デッドレター |
| スキーマ変更 | 両側を同じコミットで直す | 追加のみ。破壊的変更は新しい型を並走 |

**左の列で 1 行だったものが、右の列では 1 節になります。** これが「プロセスを越える」ことの代金です。

得たものも明確です。予約の確定が trackingms の可用性に縛られなくなり、サービスごとに独立してデプロイ・スケールできます。ADR-001 が挙げたポジティブはそのまま成立しています。

**払った代金は、検査の量に現れます。** `TrackingNumberIssuedEvent` の 1 本に対して、プロデューサ側の契約テスト 3 本・コンシューマ側の契約テスト 4 本・実ブローカーの往復テスト 5 本の**計 12 本**が置かれています。ここに交換機の宣言と発行アダプタの単体テスト、そしてアーキテクチャ規則（触れる場所・購読しない・発行しない）が加わります。モジュラーモノリスでは、この保証の大半をコンパイラが黙って与えていました。

## まとめ

### 配送手段が変わったとき、何が変わらなかったか

1. ドメインもユースケースも出力ポートしか知らない。変わったのは実装クラスの中身だけ
2. 受信側は `interfaces/events/` に置き、そこだけがメッセージ基盤を知る
3. ヘキサゴナルの配置は、**プロセス境界が入ったときに代金を回収する**

### 何が新しく要ったか

1. **契約**——名前・項目・型 ID を、両側が同じ 1 つから読む
2. **到達の確認**——「書いた」ことと「届く」ことは別。実ブローカーで往復させる
3. **失敗の行き先**——受け取れなかったもの（デッドレター）と、どこにも入らなかったもの（予備の交換機）は別
4. **冪等**——再試行がある以上、二重配送は起こる
5. **コミットとの前後**——境目が無ければ機構は素通りする

### そして、緑では分からないことについて

ADR-022 の後日談 3 件は、いずれも全テストが緑のまま守られていませんでした。**検査が本番と違う条件で回っていた**からです。イベントの配線がずれても**送り手はエラーになりません**。REST なら 4xx か 5xx が返るところで、何も起きません。

**この性質が、EDA の検査を REST の検査より高くしています。** 契約テストを両側に置き、往復を実ブローカーで確かめ、否定の決定まで検査に落とす——ここまでやって、ようやく「届いている」と言えます。

## 次に何が要るか

本章で扱ったのは、**プロセスを越える配送**までです。第 3 章の末尾で挙げた 3 つの条件のうち、残る 2 つはまだ埋まっていません。

**取りこぼしを数えるだけでは済まなくなったとき。** この実装は 3 回再試行してデッドレターへ送りますが、そこから戻す操作は手動です。Transactional Outbox も入っていません。**コミット後・発行前の窓は開いたままです。**

**イベントの履歴そのものが業務要件になったとき。** この実装にもイベントストアはありません。RabbitMQ は配送の手段であって、記録ではありません。読まれたメッセージは消えます。**「いつ何が起きたか」を後から引ける場所は、どちらの実装にもまだありません。**

CQRS についても同じです。両実装ともコマンドとクエリのサービス分離までは行っていますが、**投影テーブルも読み取り専用のモデルもありません**。第 5 章として Event Sourcing を扱うには、それを実装した参照元が要ります。

**動いていないコードについて設計を語ることはしません。** 次章を起こすのは、対応する実装を収録できてからです。
