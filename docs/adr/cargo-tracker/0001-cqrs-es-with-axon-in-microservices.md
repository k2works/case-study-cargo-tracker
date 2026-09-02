---
type: ADR
title: "ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でマイクロサービスとして実装する"
description: "CQRS / Event Sourcing を Axon Framework 5 のマイクロサービスとして実装する決定。配置の形・ES の適用範囲・Axon 5 系 API の採用・サービス間の配送経路と、IT1 スパイクの結果（採用版 5.1.0-RC2・Saga 廃止）。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-02T21:34:44Z }
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
  - { by: human:kakimomokuri, at: 2026-09-02T12:47:29Z }
---

# ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でマイクロサービスとして実装する

国際貨物輸送管理システム（Cargo Tracker）の `java/take-8` を、Axon Framework 5 による CQRS + Event Sourcing で、**BC ごとに独立デプロイするマイクロサービス（7 サービス + Gateway + 共有ライブラリ）** として実装する。

日付: 2026-09-02

## ステータス

2026-09-02 提案されました

## コンテキスト

記事シリーズ「エンタープライズ Java における実践的ドメイン駆動設計（draft-2）」は、第 5 章「CQRS / Event Sourcing」を**参照元ソースが無い**ことを理由に保留している（[アウトライン §5](../../article/practical-ddd-in-enterprise-java/draft-2/outline.md)）。着手条件は「Event Sourcing 実装が `docs/article/source/` に収録されること」である。`take-8` はその参照元になる。

参照できる先行実装は 2 つある。

| 参照元 | 形 | Event Sourcing |
| :--- | :--- | :--- |
| `java/take-4`（`tmp/take-4/`） | マイクロサービス 7 + Gateway、Axon Framework 5 + Axon Server | あり。ADR-0007〜0009 で Axon 5 の API と Spring Boot 4 統合の落とし穴を実機で解決済み |
| `java/take-7`（`source/java-3/`） | マイクロサービス 8 + 共有ライブラリ、RabbitMQ | なし。「初期フェーズには複雑すぎる」として見送り（`java-3` ADR-001） |

記事の第 3 章はモジュラーモノリス（`java-2`）、第 4 章はプロセスを越えるイベント（`java-3`）を扱う。第 6 章の「実装アプローチの比較と選択指針」は、この 2 つに CQRS/ES を加えた 3 つを比較する予定である。

### 決めるべきこと

1. **配置の形**：マイクロサービス（`take-4` / `java-3` を踏襲）か、モジュラーモノリスか
2. **Event Sourcing の適用範囲**：全集約か、一部か
3. **Axon Framework のバージョン**：5 系（API が 4 系と非互換）か、4 系（書籍の参考実装と同じ）か
4. **サービス間の配送経路**：Axon Server 一本か、`take-4` のように同期問い合わせだけ REST にするか

## 決定

### 1. マイクロサービスにする

`take-4` と `java-3` のサービス分割を踏襲し、BC ごとに独立した Spring Boot アプリケーションにする。Database per Service とし、サービス間は Axon Server 経由のメッセージだけで結ぶ。

根拠は「**第 4 章とプロセスの形を揃える**」ことである。第 4 章（`java-3`）はマイクロサービスで「プロセスを越えるイベント」を扱った。第 5 章も同じサービス分割にすれば、第 4 章との差分は永続化（現在状態の UPDATE → イベント列）と読み書きの分離だけになり、それがそのまま Event Sourcing の代金として第 6 章で比較できる。`java-3` が見送った Event Sourcing を、同じ分割の上で払う。

モジュラーモノリス案（第 3 章と揃える）は退けた。第 3 章と揃えると、第 4 章が払った代金（契約・配送・結果整合）を第 5 章がもう一度別の形で払うことになり、第 6 章の比較軸が「プロセス境界」と「Event Sourcing」で交差する。

| サービス | BC | 由来 |
| :--- | :--- | :--- |
| gatewayms | — | take-4 / java-3 |
| authms | Auth | java-3（US31） |
| bookingms | Booking | take-4 |
| routingms | Routing | take-4 |
| trackingms | Tracking | take-4 |
| handlingms | Handling | java-3（UC21 通関申告を含む） |
| billingms | Billing | take-4 |
| shared | 共有カーネル（ライブラリ） | take-4 ADR-0005 / 0014、java-3 |

`java-3` の simulationms は対象外とする。記事の主題に関係しない。

### 2. Event Sourcing は業務 BC の集約に適用し、authms と共有カーネルには適用しない

| 適用 | 集約 |
| :--- | :--- |
| する | bookingms（`Cargo` / `Shipper` / `Quotation`）、routingms（`Voyage`）、trackingms（`TrackingActivity`）、handlingms（`HandlingActivity` / `CustomsDeclaration`）、billingms（`Invoice`） |
| しない | authms（`User`）：現在状態だけが業務に要る。履歴は監査ログテーブルで足りる |

`take-4` は IT1 で `Shipper` を工数超過の懸念から CRUD に切り替えた経緯がある（`take-4` ADR-0007 のコンテキスト）。本プロジェクトは学習目標を優先し、業務 BC の集約はすべて Event Sourcing にする。

**`Quotation` と `Voyage` も Event Sourcing にする理由。** 履歴が業務として要る根拠（US19・US20・US28・US29）は例外処理・誤配・通関にあり、見積と航海には無い。それでも 2 つを Event Sourcing にするのは、記事第 6 章の比較のためである。「履歴が要る集約だけ ES、他は状態保存」にすると、第 4 章との差分が集約ごとに違う形になり、Event Sourcing の代金を 1 つの表で並べられない。全集約で払って初めて「線をここで引くべきだった」と書ける。US31（authms）だけは業務上も学習上も履歴が要らないので除く。

**見直しの発動条件。** 「工数の問題が出たら」では検知できないので、数値で置く。**IT2 終了時点で実績ベロシティが計画の 70% 未満なら、`Quotation` と `Voyage` を状態保存（MyBatis の UPDATE）に落とす。** 落とすときは本 ADR を改訂し、落とした理由を第 6 章の比較表に「ES を適用しなかった集約とその判断」として残す。判定は `docs/development/cargo-tracker/` のイテレーション報告書で行う。

### 3. Axon Framework 5 系（採用版 5.1.0-RC2）を採用する

**版は 5.1.0-RC2 に固定する（IT1 スパイクで確定）。** 調査時点では 5.3 系を想定していたが、`org.axonframework:axon-server-connector` は Maven Central に **5.0.0 と 5.1.0-RC2 しか公開されていない**（5.2・5.3 は非公開）。starter・`axon-test` などコア側は 5.3.1 まで出ているが、コア 5.3.1 に connector 5.1.0-RC2 を載せると `CommandBusConnector` / `QueryBusConnector` / `AxonServerConfigurationEnhancer` が解決できず **Axon Server に接続できない**（IT1 スパイクで jar のリンク検査により実測。`take-4` ADR-0009 と同型）。決定 4 が Axon Server 一本を配送経路にしている以上、connector が存在する版に全体を揃えるほかない。RC を本番構成に採るのは望ましくないが、GA の connector は 5.0.0 のみで、`take-4` の実績があるのは 5.1.0-RC2 である。**connector の 5.2 以降が GA で公開された時点で昇格を検討し、本 ADR を改訂する。**

4 系の `@Aggregate` / `@AggregateIdentifier` / `AggregateLifecycle.apply()` / `AggregateTestFixture` は 5 系に存在しない（`take-4` ADR-0007 の検証結果）。本プロジェクトは `take-4` が**最終的に**確定した 5 系のパターンを標準にする。集約の登録 API は ADR-0007 の `@EventSourcedEntity` ではなく、**ADR-0008 の `@EventSourced(idType, tagKey)`**（`org.axonframework.extension.spring.stereotype`）である。ADR-0007 の形は統合テストが `CommandGateway` をモックしていて見えず、bootJar の実機で `NoHandlerForCommandException` を出して退けられた。

| 要素 | 採用する API |
| :--- | :--- |
| 集約の登録 | `@EventSourced(idType = String.class, tagKey = "...")`（Spring stereotype）、`@EntityCreator`。`@EventSourcedEntity` 単独は使わない |
| コマンドハンドラ | `@CommandHandler`（作成系は `static`、更新系はインスタンス）。イベント発行は引数の `EventAppender` |
| 状態復元 | `@EventSourcingHandler` |
| コマンドの宛先 | `@TargetEntityId` |
| 投影 | `@EventHandler` + Processing Group、`pooled`（`PooledStreamingEventProcessor`）。投影はコマンドを送らない。**Processing Group は `@ProcessingGroup` ではなく `axon.eventhandling.processors."[<パッケージ名>]"` のパッケージキーで指定する**（`@ProcessingGroup` は Axon 5 に存在しない） |
| イベント → コマンド | `application/reaction` の Reaction Handler（`@EventHandler` + `CommandGateway`、投影と別の Processing Group）。**`@Saga` は使わない**（Axon 5 に存在しない。決定 6） |
| 問い合わせ | `@QueryHandler` + `QueryGateway`。同期問い合わせはタイムアウト 5 秒、Reaction からは呼ばない |
| テスト | `AxonTestFixture.with(ApplicationConfigurer)`（`axon-test`）。統合テストの Axon Server は `axon-test` 同梱の `org.axonframework.test.server.AxonServerContainer` を使う（自作しない） |

**ドメインが Spring stereotype を 1 つだけ持つことについて。** `@EventSourced` はメタアノテーションに `@Component` を持つ Spring の型であり、「ドメイン層は Spring に依存しない」の例外になる。これを許すのは、Axon 5 の Spring Boot 自動設定が `@EventSourced` Bean を経由してしか集約を Module として検出しないためである（`@Bean EventSourcedEntityModule` で代替すると二重登録になる。ADR-0008 の試行 B）。例外は ArchUnit の許可リストに **`org.axonframework.extension.spring.stereotype.EventSourced` の 1 型**として明示し、`org.springframework..` への直接依存は引き続き禁止する。IT1 スパイクの第 1 項目で実機検証した結果、**`@EventSourced` 単独で Command Bus に登録される**ことが確認できた（bootJar・実 Axon Server・`CommandGateway` のモック無しで、コマンド受理 → イベント保存 → 投影受信まで到達）。`@EventSourcedEntity` は 5.1.0-RC2 に存在しないため、この例外は恒久的に維持する。

4 系にダウングレードして書籍の参考実装をそのまま使う案は退ける。記事の読者が手にするのは 5 系であり、4 系の API で書いた記事は公開時点で古い。

### 4. サービス間の配送経路は Axon Server 一本にする

`take-4` は経路候補の取得（bookingms → routingms）を REST で行った。本プロジェクトは同期の問い合わせも Axon Query Bus を通す。

- 配送経路が 1 種類になり、サービスは互いの URL を知らなくてよい
- 提供側が落ちているときに `NoHandlerForQueryException` で明示的に失敗する（REST の接続エラーと違い、Axon Server が「誰も居ない」と答える）
- サービス越しに送るメッセージ（契約イベント・契約コマンド・契約クエリ）は `shared/contract/{event,command,query}` に置き、名簿を ArchUnit で固定する。設計時点の名簿は**契約イベント 11 本、契約コマンド 2 本、契約クエリ 1 本（`FindRouteCandidatesQuery`）**（`architecture_backend.md`「ドメインイベント一覧」）。名簿が増えることは結合が増えたことなので ADR を起こす
- 同期の問い合わせは bookingms → routingms の経路候補 1 本に限る。billingms が要る荷主の契約情報は `FindShipperForBillingQuery` でなく `ShipperRegisteredEvent` / `CorporateContractAssignedEvent` の購読で写す。Saga と Reaction Handler は同期クエリを呼ばない（`.join()` が Processing Group を止める）

REST はクライアントから Gateway を通って各サービスに入る経路にだけ使う。

### 5. 実装着手前のスパイク（IT1 実施済み・結果）

`take-4` の ADR が解決していない、または本プロジェクトで条件が変わる事項を IT1 のスパイク（タイムボックス 4h）で確定した。実施日 2026-09-02、環境は Java 25.0.2・Docker 29.7.2・`axoniq/axonserver:2026.0.4`（`AXONIQ_AXONSERVER_STANDALONE_DCB=true`）。**スパイクのコードは残していない。**

| # | 事項 | 結果 |
| :--- | :--- | :--- |
| 1 | **集約の登録 API**：stereotype 無しで Command Bus に登録されるか | **`@EventSourced` が必要。** `@EventSourcedEntity` は 5.1.0-RC2 に存在しない。`@EventSourced(idType, tagKey)` 単独で登録され、bootJar・実 Axon Server・`CommandGateway` のモック無しで、コマンド受理 → イベント保存 → 投影受信まで通った。決定 3 の許可リストは恒久化する |
| 2 | Spring Boot と Axon の自動設定の整合 | **Spring Boot 4.1.1 + Java 25 で成立するが、2 つの制約がある。**(a) `spring.main.allow-circular-references=true` が必須（`axon.axonserver` の `@ConfigurationProperties` と Boot の `BoundConfigurationProperties` が Bean 循環を作る。Boot 4.0.6 でも同じで、Boot の版を下げても回避できない）。(b) `TokenStore` Bean が無いと `Could not find a mandatory TokenStore` で起動失敗する（自動設定されない）。`TransactionManager` の重複・`token_entry.mask` は DB を伴う IT1 タスク 1.3 で確認する |
| 3 | `AxonTestFixture` の組み立て方 | **`AxonTestFixture.with(ApplicationConfigurer)`** が正。`EventSourcingConfigurer.create().registerEntity(EventSourcedEntityModule.autodetected(idType, entityType))` で集約を登録する。**集約の単体テストでは `with(configurer, c -> c.disableAxonServer())` が要る**：既定のままだと発行イベントが記録されず、集約が正しくても「イベントが 1 本も出ていない」形で落ちる（IT1 タスク 6.1 で実測）。例外は `CommandExecutionException` に包まれる。あわせて `axon-test` に **`org.axonframework.test.server.AxonServerContainer`**（Testcontainers、`withDcbContext(true)` を持つ）が同梱されていることが分かった。IT1 タスク 2.4 の基底クラスはこれを使い、自作しない |
| 4 | Saga のアノテーションと `SagaLifecycle` の 5 系での名称 | **Axon 5 に Saga は存在しない。** 5.0.0・5.1.0-RC2・5.3.1 のいずれの jar にも `Saga`・`Deadline`・`@ProcessingGroup` を含むクラスが 1 つも無い（Axon 4 の概念）。設計の Saga はすべて Reaction Handler で実装する（決定 6） |
| 5 | Axon Server 経由でサービス越しにコマンド・クエリが届くこと | **届く。** 集約を持たない JVM から送ったコマンドを、集約を持つ別 JVM が処理し、その投影までイベントが到達することを 2 JVM で確認した。なお接続直後に出る `CommandChannel ... 0 command handlers registered` のログは登録前の時点を映しているだけで、異常ではない |
| 6 | `axon-server-connector` の明示依存と DCB 無効時の検知 | **明示依存が必要**（starter は 5.1・5.3 とも connector を推移的に含まない）。**DCB 無効の context に繋ぐと `AXONIQ-1302 default: not found in any replication group` が出る**（設計が想定した `AXONIQ-2308` ではない。2026.0.4 での実測値）。さらに**アプリケーションは起動を止めず無限に再接続を試み続ける**ため、IT1 タスク 1.4 の起動時接続検査は必須である |
| 7 | S3 へエクスポートした Event Store からの差分再投入 | **未実施。** 1〜6 で版の前提が崩れ、その確定に時間を使った。RPO の根拠が未検証のまま残るため、IT2 のリスクとして持ち越す（`non_functional.md` の RPO 記述に「未検証」を明記する） |

**副産物として分かったこと。** コマンドの戻り値が `byte[]` のまま返る（`CommandGateway.sendAndWait` の結果を型で受けるには変換の指定が要る）。IT1 タスク 6.5 で `201` に識別子を載せるときに効くため、実装時に変換方式を決める。

### 6. Saga を使わず、状態を自分で持つイベントハンドラに一本化する

決定 5 の第 4 項でスパイクが示したとおり、Axon 5 には Saga の API が無い。設計（`domain-model.md`）が Saga と呼んでいた「イベントを受けて別の集約にコマンドを送る」調整役は、すべて `application/reaction` の Reaction Handler として実装する。

**これは代替品ではなく、Axon 5 が勧めている形である。** Axon 4 からの移行事例でも、Saga を素の `@Component` + `@EventHandler` に書き直し、Saga のインフラが持っていた状態を**自分のデータベースに明示的に持つ**（`ProcessStateService` のような専用の窓口を置く）のが推奨とされている。`SagaLifecycle.associateWith()` / `end()` は、その状態の作成・更新・削除に置き換わる。

自分で持つほうがよい理由は 4 つある。**Saga が戻ってきても、この 4 つを上回らない限り移らない**（後述「再評価の発動条件」）。

| # | 理由 |
| :--- | :--- |
| 1 | **状態が見える・引ける。** Saga のストアに直列化されて埋まるのではなく、自分のテーブルに載る。滞留の一覧化（`gulp reaction:stuck`）も管理画面も、ふつうの SQL で書ける |
| 2 | **直列化の事故が起きない。** Saga は状態を丸ごと直列化して持つので、フィールドの型を変えるとリプレイで復元に失敗する。テーブルに持てばマイグレーションの問題に落ちる |
| 3 | **テストが単純。** Saga 専用のフィクスチャが要らない。依存を差し替えるだけの、ふつうの Spring コンポーネントとして書ける |
| 4 | **枠組みに合わせなくてよい。** 連鎖の途中経過をどこに置くか（集約か専用テーブルか）を、業務の都合で選べる |

- 調整役は投影と**別の Processing Group** に置き、リプレイ対象から外す（H1 の判断は変えない）
- Saga が持っていた「関連付け（association）」と「終了（`@EndSaga`）」に相当する状態は、**その BC の集約か専用のテーブル**に持つ。フレームワークは面倒を見ない
  - 1 段で終わる連鎖（`CargoDeliveredEvent` → `CalculateInvoiceCommand` など）は、集約の状態から「今どの段か」が読めるので専用テーブルを作らない
  - **複数段にまたがり、途中で止まったことを一覧にしたい連鎖**は、専用の `process_state` テーブルを置く（`data-model.md`）。該当は予約 → 追跡開始（3 段）。滞留の検知（24 時間超）をこのテーブルの走査で行う
- タイムアウト起点の処理（Deadline）も無いため、期限で動く業務は**投影テーブルを定期に走査する運用ジョブ**として設計する。該当は `operation.md` の要確認一覧の督促

**「無い」ことの確かめ方（2026-09-03 実施）。** 公式リファレンスの [Sagas](https://docs.axoniq.io/axon-framework-reference/5.1/sagas/) は 4 ページとも冒頭に "Sagas do not have a replacement yet in Axon Framework 5." と書いており、本文は Axon 4 の API 解説がそのまま残っている。載っているクラスが実在するかを成果物で照合した結果が次のとおり。

| ページに出てくるもの | Axon 4.11.2 | Axon 5.1.0-RC2（全 9 成果物・1417 クラス） |
| :--- | :--- | :--- |
| `AnnotatedSagaManager` / `SagaLifecycle` / `AssociationValue` | あり（`org.axonframework.modelling.saga`） | **なし** |
| `JdbcSagaStore` / `JpaSagaStore` / `InMemorySagaStore` / `CachingSagaStore` | あり | **なし** |
| `EventProcessingConfigurer.registerSaga()` | あり | クラスは別パッケージにあるが saga を含むメソッドは 0 個 |

あわせて `org.axonframework:axon-saga` という成果物が 5.0.0・5.1.0・5.2.0・5.3.1 のいずれにも存在しないこと、最新の 5.3.1 のコア 10 jar にも `saga` を含むクラスが 0 件であることを確認した。infrastructure のページには依存の記載自体が無い。

### 再評価の発動条件

「代替が出たら考える」では検知できないので、**判定できる条件**にする。次のどちらかが成り立ったら本決定を再評価し、必要なら ADR を改訂する。

| # | 発動条件 | 判定方法 |
| :--- | :--- | :--- |
| 1 | 採用中の Axon の成果物に Saga のクラスが公開された | `SagaIsStillAbsentTest`（Axon のクラスパスに `saga` を含むクラスが現れたら**赤**にする）。版を上げたときに落ちて気づける |
| 2 | 公式リファレンスの Sagas から "do not have a replacement yet" の断り書きが消えた | 版を上げるときに [Sagas](https://docs.axoniq.io/axon-framework-reference/5.1/sagas/) を読む。`tech_stack.md` の版上げ手順に含める |

**検査を置く理由。** 発動条件を文章だけで持つと、版を上げたときに誰も読み返さない（同シリーズで、ADR の規則が 7 イテレーションのあいだ半分守られなかったことがある）。条件 1 は検査に落とし、条件 2 は版上げの手順に載せる。

再評価では次を比べる。**Saga が公開されても自動では移らない。**

- 上の 4 つの理由（状態が見える・直列化の事故が無い・テストが単純・置き場を選べる）を、Saga の関連付けが上回るか。**上回らないなら移らない**
- Deadline の有無。運用ジョブでの定期走査を置き換えられるか
- 移行の代金。`saga_entry` / `association_value_entry` の追加と、既存の Reaction Handler の書き換え

## 影響

### 得るもの

- 全集約の完全な履歴。例外処理（US19・US20・US28）と通関（US29）の「いつ・誰が・何を根拠に」がイベント列から追える
- 読み取りモデルをサービスごと・画面ごとに最適化でき、JOIN に頼らない
- 記事第 5 章の参照元が成立し、第 4 章と同じサービス分割で第 6 章の 3 アプローチ比較が可能になる

### 払うもの

- イベントが永続化フォーマットになる。フィールドの削除・型変更ができず、Upcaster とゴールデンファイルの契約テストが要る
- 投影は非同期。登録直後に一覧へ出ない状態を API と画面が扱う
- 投影テーブルは派生データであり、列の追加はマイグレーションでなくリプレイで埋める。リプレイ手順がサービスごとに運用に加わる
- Axon Server というミドルウェアが 1 つ増え、全サービスの単一障害点になる（[ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md)）
- サービスごとの DB・デプロイ・監視が 7 つ分要る（`java-3` と同じ代金）
- Axon 5 系の公開情報が少なく、API の確認にスパイクが要る
- **connector が公開されている版に全体が縛られる。** `axon-server-connector` は 5.0.0 と 5.1.0-RC2 しか無く、コアだけ新しくすると Axon Server に繋がらない。RC 版を本番構成に採る（決定 3）
- **Saga と Deadline をフレームワークが持たない。** 調整役の状態管理と期限起動を自前で設計する（決定 6）
- **`spring.main.allow-circular-references=true` を全サービスで有効にする。** Axon 由来の循環を通すために、本来検出したい他の循環まで通ってしまう

### 設計ドキュメントへの波及

| ドキュメント | 内容 |
| :--- | :--- |
| `architecture_backend.md` | 本 ADR の判断を前提に作成済み |
| `architecture_infrastructure.md` | 7 サービス + Gateway + Axon Server + PostgreSQL × 6 の配置。`java-3` の kind / Heroku / ECS 構成を参照 |
| `domain-model.md` | 集約ごとにコマンド・イベント・状態遷移を定義する。イベントは集約の永続化フォーマットとして設計し、契約イベントを区別する |
| `data-model.md` | サービスごとの投影テーブルと Axon の管理テーブル（`token_entry`。**`saga_entry` / `association_value_entry` は Axon 5 に Saga が無いため作らない**）を定義する。Event Store のスキーマは Axon Server が持つ |
| `test_strategy.md` | 集約・投影・Reaction Handler・イベント契約・ArchUnit の 5 種と、サービス間ダイヤモンドを定める |
| `operation.md` | Event Store のバックアップとサービス単位のリプレイ手順を定める |

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| サービス分割 | `settings.gradle` の `include` から**テスト専用サブプロジェクト（`contract-tests`・`acceptance-tests`）を除いたもの**が上の 8 つと一致すること |
| サービス間は Axon Server だけ | ビルド：各サービスの本番クラスパスに他サービスの成果物が無いこと。ArchUnit：`RestClient` / `RestTemplate` を `infrastructure/acl` で使わないこと |
| 共有カーネルの範囲 | ArchUnit：`shared` に置けるパッケージの名簿（`domain/model`・`domain/auth`・`contract/*`・`infrastructure/axon`・`infrastructure/time`）を固定する |
| 契約の名簿 | ArchUnit：送信・購読の引数型が `shared/contract` 以外のイベント・コマンド・クエリをサービス越しに使えば赤（契約イベント 11・コマンド 2・クエリ 1） |
| サービス越しの同期状態変更を置かない | ArchUnit：`CommandGateway` の利用箇所を `interfaces`・`application/reaction` に限定する。`infrastructure/projection` は `CommandGateway` に依存しない |
| 投影がコマンドを送らない | 統合テスト `ReplayIT`：投影の Processing Group をリセットしてリプレイし、`CommandGateway` が 1 度も呼ばれないこと |
| Reaction は同期クエリを呼ばない | ArchUnit：`application/reaction` が `QueryGateway` に依存しない |
| Saga を使わない（決定 6） | ArchUnit：`org.axonframework..saga..` への依存と `application/saga` パッケージの存在を禁止する。名簿方式にせず「その名前の型・パッケージがあれば赤」にする |
| Saga の再評価の発動条件（決定 6） | `SagaIsStillAbsentTest`：Axon のクラスパスに `saga` を含むクラスが現れたら赤にする。版を上げたときに落ちて気づける。あわせて「Axon の jar を実際に開いているか」も見る（開けていなければ「無い」でなく「調べていない」で緑になる） |
| Axon の版が揃っている（決定 3） | ビルド：`libs.versions.toml` の `axon` は単一の version.ref であり、starter・connector・`axon-test` がすべてそれを参照すること。参照していない Axon 依存があれば赤にするテストを置く |
| `@ProcessingGroup` を使わない（決定 3） | ArchUnit：`@ProcessingGroup` 相当の型参照が無いこと（存在しないのでコンパイルで止まる）。Processing Group の割当は `application.yml` のパッケージキーで行い、投影のパッケージごとに 1 件あることを統合テストで数える |
| ドメイン層のフレームワーク非依存 | ArchUnit：Spring・MyBatis への依存を禁止。Axon は `..annotation..`・`EventAppender`・`org.axonframework.extension.spring.stereotype.EventSourced` の許可リストのみ |
| authms は Event Sourcing にしない | ArchUnit：`auth` パッケージに `@EventSourced` / `@EventSourcedEntity` が無いこと |
| 4 系 API を使わない | ビルド：`org.axonframework.modelling.command.AggregateLifecycle` 等への参照が無いこと（存在しないのでコンパイルで止まる） |
| `axon-server-connector` の接続と DCB | 起動時のヘルスチェック。接続できない、または context が DCB でなければ**起動を止める**（既定では止まらず無限再試行することをスパイクで確認済み）。統合テストで DCB 無効の Axon Server に対して起動が止まることを 1 本固定する。判定は `AXONIQ-1302` のログ検出に頼らず、接続後に context の DCB 可否を問い合わせて行う |
| スパイクの結果を ADR に戻す | IT1 の DoD：決定 5 の 7 項目それぞれの結果で本 ADR・`architecture_backend.md`・`tech_stack.md` を更新してから IT1 をクローズする（2026-09-02 実施。第 7 項目のみ IT2 へ持ち越し） |

## 備考

- 著者: claude-code/claude-fable-5-1（分析フェーズ、`orchestrating-analysis` → `analyzing-architecture`）
- 改訂: 2026-09-02 初稿はモジュラーモノリスを提案したが、ユーザーの指示によりマイクロサービスに変更した
- 改訂: 2026-09-02 設計レビュー（`docs/review/cargo-tracker/設計_review_20260902.md` H2）で、決定 3 の集約 API が `take-4` ADR-0007 の形（`@EventSourcedEntity` 単独）のままで、ADR-0008 が実機で退けた経緯（`NoHandlerForCommandException`）を反映していないと指摘された。決定 3 を `@EventSourced(idType, tagKey)` に訂正し、Spring stereotype を 1 つ許す理由と IT1 スパイク第 1 項目を追記した。あわせて決定 2 に ES 適用範囲の理由と見直しの発動条件（M5・H8）、決定 4 に契約の数（H6・M1・M18）、コンプライアンスに DCB（H3）・Reaction Handler（H1）・`contract-tests` の除外（M13）を追加した
- 参照元: `tmp/take-4/docs/adr/0001-axon-framework-adoption.md`、`0004-microservice-decomposition.md`、`0007-axon-5-event-sourcing-api.md`、`0008-axon-5-spring-boot-integration-pattern.md`、`0009-axon-server-connector-explicit-dependency.md`、`0014-shared-module-event-classes.md`
- 参照元: [java-3 ADR-001](../../article/source/java-3/docs/adr/001-microservices-architecture.md)（Event Sourcing 見送りの判断）、[java-3 ADR-022](../../article/source/java-3/docs/adr/022-domain-event-contract.md)
- 記事: [draft-2 アウトライン §5](../../article/practical-ddd-in-enterprise-java/draft-2/outline.md)
- Axon Framework の版は調査時点（2026-09-02）で 5.3 系。確定は `tech_stack.md`
- 改訂: 2026-09-02 IT1 のスパイクを実施し、決定 5 を「確定する事項」から「結果」に書き換えた。前提が 3 つ崩れたため決定を改めた。(1) `axon-server-connector` が 5.2 以降に無く、コアだけ 5.3 にすると Axon Server に接続できないため、**採用版を 5.3 系から 5.1.0-RC2 に変更**した（決定 3）。(2) **Axon 5 に Saga・Deadline・`@ProcessingGroup` が存在しない**ため、Saga を Reaction Handler に一本化する決定 6 を追加した。(3) Spring Boot と Axon の同居に `spring.main.allow-circular-references=true` と明示的な `TokenStore` Bean が要ることが分かった。DCB 無効時のエラーは `AXONIQ-2308` ではなく `AXONIQ-1302` で、かつ起動が止まらないことも実測した
- 改訂: 2026-09-03 決定 6 に再評価の発動条件を追記した。公式リファレンスの Sagas に 4 系の API 解説が残っているため「5 系にも Saga がある」と読めるが、4 ページとも冒頭に "Sagas do not have a replacement yet in Axon Framework 5." と書かれており、載っているクラス（`AnnotatedSagaManager`・`SagaLifecycle`・`AssociationValue`・各 `SagaStore`）は Axon 4.11.2 には存在し 5.1.0-RC2 の全 9 成果物には存在しない。`org.axonframework:axon-saga` も 5.x のどの版にも無い。「代替が出たら考える」では検知できないので、発動条件 1（Axon に Saga のクラスが公開されたら）を `SagaIsStillAbsentTest` として検査に落とし、発動条件 2（リファレンスの断り書きが消えたら）は版上げの手順に載せた
- 改訂: 2026-09-03 決定 6 を「Saga を使わず、状態を自分で持つイベントハンドラに一本化する」に改めた。Axon 4 からの移行事例で、Saga を素の `@Component` + `@EventHandler` に書き直し、Saga のインフラが持っていた状態を自分のデータベースに明示的に持つ（`ProcessStateService` のような専用の窓口を置く）のが推奨とされていることを確認したため。これは「Saga が無いので仕方なく」ではなく Axon 5 が勧めている形である。自分で持つほうがよい理由（状態が見える・直列化の事故が無い・テストが単純・置き場を選べる）を明記し、再評価は「Saga が戻ってきてもこの 4 つを上回らない限り移らない」と条件を強めた。あわせて、複数段にまたがる連鎖の途中経過を置く `process_state` テーブルを `data-model.md` に定義し、`domain-model.md` の予約 → 追跡開始（3 段）をそれに合わせた
