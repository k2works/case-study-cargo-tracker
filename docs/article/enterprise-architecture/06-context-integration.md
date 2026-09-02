---
type: Article
title: "第 6 章：コンテキスト間連携 — ACL とドメインイベント"
description: "コンテキスト間の越境手段である ACL ポート 27 本とドメインイベント 9 種、そして結果整合の代償。"
tags: [article, enterprise-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-14T09:07:33Z }
---

# 第 6 章：コンテキスト間連携 — ACL とドメインイベント

| 項目 | 内容 |
| :--- | :--- |
| 観点 | アプリケーションアーキテクチャ |
| 一次資料 | ADR-009 / 012 / 021・`shared/domain/event/`・各 BC の `acl/` |
| 主題 | 境界を越える経路を、何を基準に選ぶのか |

## 越境の 2 系統

境界を引いた以上、越える手段が要ります。この実装は 2 系統を持ちます。

| 系統 | 手段 | 数 | 故障モード |
| :--- | :--- | ---: | :--- |
| **同期** | ACL ポート（インタフェース＋別 BC のアダプタ） | 27 ポート／27 アダプタ | 呼び出し側にその場で失敗が返る |
| **非同期** | ドメインイベント（`@TransactionalEventListener(AFTER_COMMIT)`） | 9 イベント／11 ハンドラ | 発行側は成功し、購読側の反映は後から起きる（あるいは起きない） |

**この 2 つは技術の選択肢ではなく、業務の選択です。** どちらを選ぶかで「誰が失敗に気づくか」が変わります。ADR-021 がその判断基準を明文化しています。

## ACL ポート — 全 27 本

BC 別の内訳です。

| 定義する BC | ポート | 実装する BC |
| :--- | :--- | :--- |
| **Billing**（7） | `BillableCargoPort`・`BookingSettlementPort`・`InvoiceNotificationPort` | Booking |
| | `ShipperDiscountPort`・`ShipperContactPort` | Shipper |
| | `TrackingStatusPort`・`CargoExceptionRecordsPort` | Tracking |
| **Booking**（8） | `ShipperExistenceChecker` | Shipper |
| | `VoyageCapacityPort`・`RouteRelaxations` | Routing |
| | `TrackingPort`・`CargoExceptions`・`CargoCurrentLocation` | Tracking |
| | `CargoCorrectionRequests` | Handling |
| | `KnownPorts` | （場所マスタ） |
| **Routing**（4） | `RoutableBookings`・`CargoRouteAssignments`・`AffectedBookings` | Booking |
| | `KnownPorts` | （場所マスタ） |
| **Tracking**（3） | `CargoContacts` | Booking |
| | `CustomsStatuses` | Handling |
| | `PortNames` | （場所マスタ） |
| **Handling**（2） | `CargoSnapshots` | Booking |
| | `ApprovedCancellations` | Booking |
| **Estimation**（2） | `RouteCandidateSource` | Routing |
| | `KnownPorts` | （場所マスタ） |
| **Shipper**（1） | `LinkedAccounts` | Security |

### 命名に 2 系統ある

**Billing のポートだけが `〜Port` で終わり、他の BC は業務名詞です。**

| BC | 命名 | 例 |
| :--- | :--- | :--- |
| Billing | `〜Port` | `ShipperDiscountPort`・`TrackingStatusPort` |
| 他の 6 BC | 業務名詞（複数形が多い） | `RoutableBookings`・`CargoSnapshots`・`ApprovedCancellations` |

**業務名詞のほうが後から採用された形です。** `RoutableBookings`（経路を割り当てられる予約たち）という名前は、**呼ぶ側が相手に何を期待しているか**を語ります。`BookingPort` では「Booking BC に繋がる口」としか言っておらず、Routing が Booking の何を必要としているかが名前から消えます。

**ACL の名前は、相手の BC 名ではなく、自分が欲しい概念で付ける。** これが ACL（腐敗防止層）の趣旨に沿った命名です。相手の名前を使うと、その時点で相手のモデルが自分の語彙に入ってきます。

**旧命名が残っているのは、既に動いているものを機械的に改名しなかったためです。** 一貫していないこと自体は負債ですが、命名が語る設計意図の違いは読み取れます。

### `KnownPorts` / `PortNames` — 3 BC が同じものを別名で持つ

Booking・Estimation・Routing が `KnownPorts`、Tracking が `PortNames` という名前で、いずれも **場所マスタ（`location` テーブル）を引くポート**を持っています。実装はすべて各 BC の `LocationMasterAdapter` です。

**同じデータを 4 つのポートで引いている**わけです。冗長に見えますが、これは ADR-005 の帰結です。`Location` は共有カーネルにありますが、**「有効な港の一覧を引く」という操作は共有カーネルには無い**からです。値オブジェクトは共有しても、問い合わせの手段は共有しません。

各 BC が自分の語彙でポートを定義した結果、`KnownPorts`（知っている港）と `PortNames`（港の名前）という**関心の違いが名前に出ています**。Tracking は追跡画面に港の表示名を出したいだけで、有効性の検証はしません。

## ドメインイベント — 全 9 種

すべて `shared/domain/event/` に置かれ、すべて `record` です。

| イベント | 発行 | 購読 | 運ぶ事実 |
| :--- | :--- | :--- | :--- |
| `CargoRoutedEvent` | Booking | Tracking | 経路が割り当てられた（目的地・推定到着日） |
| `CargoStatusUpdatedEvent` | Tracking | Booking | 輸送状態が変わった |
| `CargoCancelledEvent` | Booking | Billing | 予約がキャンセルされた |
| `CargoExceptionRaisedEvent` | Tracking | Booking | 輸送例外が起きた |
| `CargoExceptionResolvedEvent` | Tracking | Booking | 輸送例外が解決した |
| `HandlingActivityRegisteredEvent` | Handling | Tracking・Booking | 荷役作業が記録された |
| `CustomsStatusChangedEvent` | Handling | Tracking | 通関状態が変わった |
| `ClaimCancelledEvent` | Handling | Booking・Tracking | 引取が取り消された |
| `VoyageRescheduledEvent` | Routing | Booking | 航海の日程が変わった |

購読ハンドラは 11 本です（Booking 5・Tracking 5・Billing 1）。**1 イベントを 2 BC が購読する**ケースが 2 件あります（`HandlingActivityRegisteredEvent`・`ClaimCancelledEvent`）。

### イベントの置き場所は共有領域

イベントは `shared` にあります。**発行側の BC ではありません。** これは意図的です。

発行側に置くと、購読側は発行側のパッケージを import することになり、**BC 間参照禁止のルールに引っかかります**。共有領域に置くことで、両者が第三の場所を見る形になります。

ただし前章で見たとおり、共有領域は BC 間参照の検査から除外されるため、**そこに何を置いても素通り**します。だから専用のルールが立っています。

```java
    @ArchTest
    static final ArchRule 共有イベントは事実を運ぶレコードのみ =
            classes()
                    .that().resideInAPackage("com.example.cargotracker.shared.domain.event..")
                    .and().areTopLevelClasses()
                    .should().beRecords()
                    .andShould().haveSimpleNameEndingWith("Event")
                    .because("shared.domain.event は BC 間で運ぶ「起きた事実」の置き場である"
                            + "（ADR-005 / ADR-009）。命令や業務ロジックを置くと、"
                            + "イベントの形をした直接呼び出しになる");
```

> 転記元：`apps/cargo-tracker/src/test/java/com/example/cargotracker/PackageStructureTest.java`

**「イベントの形をした直接呼び出し」を防ぐのがこのルールの目的です。** `record` かつ `〜Event` という制約は、**命令（`〜Command`）を置けなくする**ためにあります。命令を共有領域に置くと、発行側が購読側にやってほしいことを名指しすることになり、疎結合が形だけになります。

ネストした型にも `record` を強制する別ルールがあり、理由はこうです。

> 事実の一部が可変だと、購読側が受け取った後で書き換えられる。

**購読側が複数ある以上、1 番目の購読者が書き換えた値を 2 番目が受け取る**という事故が起こり得ます。不変性は疎結合の前提条件です。

## ADR-009 — 判断が一度反転している

**この題材で最も重要な設計判断の履歴です。** ADR-009 は当初「BC 間 ACL は同期・同一トランザクションで呼ぶ」と決め、**改訂で反転しました**。

反転の理由を一次資料から引用します。

> 反転の理由は「**1 つの操作が 3 つの集約を 1 トランザクションで更新する形が、集約境界の
> 原則からの逸脱として重すぎた**」ことである。当初案はその逸脱を「業務上あり得ない中間状態を
> 作らないため」と正当化していたが、その代償として **BC 間の結合が強く、片方の遅延が
> もう片方を止める**構造を選んでいた。荷役は最も頻度の高い操作であり、**追跡や予約の
> 都合で荷役の記録が失敗してはならない**。順序が逆だった。
>
> 転記元：`docs/adr/009-domain-events-for-cross-context-propagation.md`

### この判断の本体は「何を落としてはいけないか」

技術的な議論（トランザクション境界・整合性モデル）ではなく、**業務上の優先順位**で決まっています。

- 荷役作業員は港で貨物を扱っています。**記録が失敗したら、作業をやり直せません**
- 追跡状態の反映が数秒遅れても、追跡管理者は困りません

だから **記録は必ず残り、反映は追って行われる**のが正しい順序でした。同期にすると、この順序が逆になります。

さらに 3 つ目の根拠が DDD の観点として鋭いものです。

> **発行側は購読側を知らない。** 荷役は「JPOSA で V001 に積み込んだ」という事実だけを伝え、
> それが輸送状態のどれに当たるか・輸送開始にあたるかは購読側が決める。
> 同期呼び出しでは、発行側が相手の関心事（`markMisrouted` / `startTransportIfNotStarted`）を
> 名前で知ることになっていた。

**同期の ACL は、呼び出すメソッド名の形で相手の関心事を持ち込みます。** `trackingPort.markMisrouted()` と書いた時点で、Handling は「誤配」という Tracking の概念を知っています。イベントにすると、Handling が語るのは「積み込んだ」という事実だけになります。

### 何を同期のままにしたか

ADR-009 はすべてをイベントにしたわけではありません。

| 種別 | 扱い |
| :--- | :--- |
| **状態の伝播**（起きた事実を他 BC が自分のモデルに反映する） | ドメインイベント |
| **問い合わせ**（相手の状態を読むだけ） | 同期の ACL ポート |
| **コマンド**（相手に何かをさせる） | 同期の ACL ポート |

## ADR-021 — 分類が判断に委ねられていた

**ADR-009 の分類には曖昧さが残りました。** 「状態の伝播」と「コマンド」の線引きが判断に任されていたのです。

その曖昧さが欠陥になった実例が記録されています。

> 入金確認の後に予約を `SETTLED` にする `BookingSettlementPort.settle` は `boolean` を
> 返す契約なのに、**呼び出し側が戻り値を捨てていた**。結果として
> 「入金確認済みだが予約が精算済みでない」請求書がログにも画面にも残らない。
>
> **その予約は精算後も引取記録を訂正できてしまう**（US36 のガードが `booking_status` に
> 依存しているため）。テストは全緑、SonarQube も PASS のまま、レビューで初めて出た。
>
> 転記元：`docs/adr/021-cross-context-state-change-must-name-where-failure-surfaces.md`

**同期を選んだのに、失敗が誰にも届かない**——同期にした利点がそのまま失われた形です。

ADR-021 が置いた基準はこうです。

> 1. **その越境は状態を変えるか。** 変えないなら問い合わせであり、同期の ACL ポートでよい
> 2. 変えるなら、**できなかったことを誰がいつ知り、その人は動けるか**

| 誰が知るか | 選ぶ形 | 失敗の届け先 |
| :--- | :--- | :--- |
| **いま操作している利用者**が知って、その場で手を打てる | **同期の ACL ポート** | **画面のメッセージ**（＋監査ログ） |
| 操作している人は何もできない（別の人・別の時間の仕事） | **ドメインイベント** | **ログ**＋**気づく手段**（一覧・カード） |

**判断基準が「トランザクション境界」でも「集約の粒度」でもなく、「誰が動けるか」である**ことがこの ADR の核心です。アーキテクチャの判断を、業務の担当者の可動域で決めています。

そして 1 行が付されています。

> **「例外にしない」は「記録しない」ではない。**

### 名簿に登録する

ADR-021 は基準を定めるだけでなく、**同期ポートを名簿に登録することを強制**しました。`CrossContextPortPolicyTest` がその名簿と実装を突き合わせます。

| 経路 | 形 | 失敗の届け先 |
| :--- | :--- | :--- |
| `BookingSettlementPort.settle` | 同期 | 請求書詳細の警告＋監査ログ |
| `InvoiceNotificationPort.notifyIssued` | 同期 | 請求書詳細の警告＋監査ログ |
| `TrackingPort.issue` | 同期 | 画面のメッセージ |
| `CargoRouteAssignments.assign` | 同期 | 画面のメッセージ |
| `CargoCancelledEvent`（US30） | イベント | ログ＋請求対象一覧 |

**「失敗の届け先」の列が名簿の本体です。** ポートを追加するとき、この列を埋められないなら設計が終わっていない——という形で、検査が設計を強制しています。

## 結果整合の代償を観測する

イベントを選ぶと、**購読側の反映が起きなかったことが静かに起こり得ます**。この実装はそれを観測する仕組みを持っています。

```text
shared/infrastructure/observability/EventualConsistencySkips.java
```

**「スキップ」を数える**クラスです。購読側が「この事実は自分には関係ない」あるいは「今の状態では反映できない」と判断して何もしなかった回数を記録します。

対応する検査が 2 本あります。

| テスト | 検査内容 |
| :--- | :--- |
| `EventualConsistencyPropagationTest` | イベントが発行され、購読側の状態が実際に変わること |
| `EventualConsistencyListenerPhaseTest` | 購読が `AFTER_COMMIT` フェーズで動くこと |

**フェーズを検査するテストがある**のが重要です。`@TransactionalEventListener` のフェーズ指定を間違えて `BEFORE_COMMIT` にすると、**発行側のトランザクションの中で購読側が動きます**。動作としては正しく見え、テストも緑になりますが、ADR-009 が反転で得たもの（発行側の成功が購読側に依存しない）が失われます。

**注釈の引数 1 つで設計判断が無効になるため、注釈そのものを検査対象にしています。**

## この章の要点

| 観察 | 内容 |
| :--- | :--- |
| 越境は 2 系統 | 同期 ACL 27 本と非同期イベント 9 種。**技術の選択ではなく業務の選択** |
| ACL の命名 | 相手の BC 名ではなく**自分が欲しい概念**で付ける（`RoutableBookings`） |
| イベントは共有領域に | 発行側に置くと BC 間参照になる。ただし共有領域は検査の除外先なので**専用ルールで縛る** |
| ADR-009 の反転 | 判断根拠は整合性モデルではなく **「荷役の記録を落としてはならない」という業務の優先順位** |
| ADR-021 の基準 | **できなかったことを誰がいつ知り、その人は動けるか。** 動けないならイベント |
| 名簿の本体 | 「失敗の届け先」を書けないポートは、設計が終わっていない |
| フェーズの検査 | 注釈の引数 1 つで ADR-009 の成果が消えるため、注釈を検査する |

次章からデータアーキテクチャに降ります。この 7 BC が、25 のテーブルにどう落ちたかを見ます。
