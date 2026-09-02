---
type: ADR
title: "ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でマイクロサービスとして実装する"
description: "CQRS / Event Sourcing を Axon Framework 5 のマイクロサービスとして実装する決定。配置の形・ES の適用範囲・Axon 5 系 API の採用・サービス間の配送経路と、着手前スパイクで確定する事項。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T07:46:35Z }
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
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

### 3. Axon Framework 5 系（調査時点 5.3）を採用する

4 系の `@Aggregate` / `@AggregateIdentifier` / `AggregateLifecycle.apply()` / `AggregateTestFixture` は 5 系に存在しない（`take-4` ADR-0007 の検証結果）。本プロジェクトは `take-4` が**最終的に**確定した 5 系のパターンを標準にする。集約の登録 API は ADR-0007 の `@EventSourcedEntity` ではなく、**ADR-0008 の `@EventSourced(idType, tagKey)`**（`org.axonframework.extension.spring.stereotype`）である。ADR-0007 の形は統合テストが `CommandGateway` をモックしていて見えず、bootJar の実機で `NoHandlerForCommandException` を出して退けられた。

| 要素 | 採用する API |
| :--- | :--- |
| 集約の登録 | `@EventSourced(idType = String.class, tagKey = "...")`（Spring stereotype）、`@EntityCreator`。`@EventSourcedEntity` 単独は使わない |
| コマンドハンドラ | `@CommandHandler`（作成系は `static`、更新系はインスタンス）。イベント発行は引数の `EventAppender` |
| 状態復元 | `@EventSourcingHandler` |
| コマンドの宛先 | `@TargetEntityId` |
| 投影 | `@EventHandler` + Processing Group、`pooled`（`PooledStreamingEventProcessor`）。投影はコマンドを送らない |
| イベント → コマンド | `application/reaction` の Reaction Handler（`@EventHandler` + `CommandGateway`、投影と別の Processing Group）または `@Saga` |
| 問い合わせ | `@QueryHandler` + `QueryGateway`。同期問い合わせはタイムアウト 5 秒、Saga / Reaction からは呼ばない |
| テスト | `AxonTestFixture`（`axon-test`） |

**ドメインが Spring stereotype を 1 つだけ持つことについて。** `@EventSourced` はメタアノテーションに `@Component` を持つ Spring の型であり、「ドメイン層は Spring に依存しない」の例外になる。これを許すのは、Axon 5 の Spring Boot 自動設定が `@EventSourced` Bean を経由してしか集約を Module として検出しないためである（`@Bean EventSourcedEntityModule` で代替すると二重登録になる。ADR-0008 の試行 B）。例外は ArchUnit の許可リストに **`org.axonframework.extension.spring.stereotype.EventSourced` の 1 型**として明示し、`org.springframework..` への直接依存は引き続き禁止する。IT1 スパイクの第 1 項目で 5.3 系が stereotype 無しで登録できると分かれば、許可リストから外して `@EventSourcedEntity` に戻し、本表を改訂する。

4 系にダウングレードして書籍の参考実装をそのまま使う案は退ける。記事の読者が手にするのは 5 系であり、4 系の API で書いた記事は公開時点で古い。

### 4. サービス間の配送経路は Axon Server 一本にする

`take-4` は経路候補の取得（bookingms → routingms）を REST で行った。本プロジェクトは同期の問い合わせも Axon Query Bus を通す。

- 配送経路が 1 種類になり、サービスは互いの URL を知らなくてよい
- 提供側が落ちているときに `NoHandlerForQueryException` で明示的に失敗する（REST の接続エラーと違い、Axon Server が「誰も居ない」と答える）
- サービス越しに送るメッセージ（契約イベント・契約コマンド・契約クエリ）は `shared/contract/{event,command,query}` に置き、名簿を ArchUnit で固定する。設計時点の名簿は**契約イベント 11 本、契約コマンド 2 本、契約クエリ 1 本（`FindRouteCandidatesQuery`）**（`architecture_backend.md`「ドメインイベント一覧」）。名簿が増えることは結合が増えたことなので ADR を起こす
- 同期の問い合わせは bookingms → routingms の経路候補 1 本に限る。billingms が要る荷主の契約情報は `FindShipperForBillingQuery` でなく `ShipperRegisteredEvent` / `CorporateContractAssignedEvent` の購読で写す。Saga と Reaction Handler は同期クエリを呼ばない（`.join()` が Processing Group を止める）

REST はクライアントから Gateway を通って各サービスに入る経路にだけ使う。

### 5. 実装着手前にスパイクで確定する事項

`take-4` の ADR が解決していない、または本プロジェクトで条件が変わる事項は IT1 のスパイク（タイムボックス 4h）で確定し、本 ADR を更新する。

| # | 事項 | 背景 |
| :--- | :--- | :--- |
| 1 | **集約の登録 API**：5.3 系で `@EventSourcedEntity` 単独（stereotype 無し）で Command Bus に登録されるか。bootJar を起動し `@MockitoBean CommandGateway` 無しで確かめる | ADR-0008。統合テストのモックが登録漏れを隠した。登録されなければ `@EventSourced` を標準とし ArchUnit の許可リストに加える（決定 3） |
| 2 | Spring Boot 4.1（Jackson 3 既定）と Axon 5.3 の自動設定の整合。`TransactionManager` Bean が 1 つであること、`SpringTransactionManager` の第 3 引数、`token_entry.mask` | `take-4` は Spring Boot 4 + Jackson 2 で JDBC 自動設定が働かず手動構成した（ADR-0009）。`TransactionManager` が複数あると `NoTransactionManager` に無音で落ちる |
| 3 | `AxonTestFixture.with(ApplicationConfigurer)` の組み立て方 | `take-4` は Mockito で代替した |
| 4 | Saga のアノテーションと `SagaLifecycle` の 5 系での名称 | `take-4` の設計は 4 系の名称で書かれており実装で未確認 |
| 5 | Axon Server 経由でサービス越しにクエリ・コマンドが届くこと | `take-4` は Query Bus をサービス越しに使っていない。`shared/contract` の型で往復することを 1 本確かめる |
| 6 | `axon-server-connector` の明示依存と、無いときのフォールバック検知。接続検査が **DCB 無効の context を赤にする**こと | ADR-0009。**無音で in-memory に落ちる**ため、起動時に接続を検査する。DCB 無効だと `AXONIQ-2308` で Coordinator が無限再試行する |
| 7 | S3 へエクスポートした Event Store からの差分再投入 | RPO の根拠。参照元で未検証 |

スパイクの結果で本 ADR の決定 3・5 と `architecture_backend.md`・`tech_stack.md` を更新することを IT1 の完了条件（DoD）に含める。

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

### 設計ドキュメントへの波及

| ドキュメント | 内容 |
| :--- | :--- |
| `architecture_backend.md` | 本 ADR の判断を前提に作成済み |
| `architecture_infrastructure.md` | 7 サービス + Gateway + Axon Server + PostgreSQL × 6 の配置。`java-3` の kind / Heroku / ECS 構成を参照 |
| `domain-model.md` | 集約ごとにコマンド・イベント・状態遷移を定義する。イベントは集約の永続化フォーマットとして設計し、契約イベントを区別する |
| `data-model.md` | サービスごとの投影テーブルと Axon の管理テーブル（`token_entry` / `saga_entry` / `association_value_entry`）を定義する。Event Store のスキーマは Axon Server が持つ |
| `test_strategy.md` | 集約・投影・Saga・イベント契約・ArchUnit の 5 種と、サービス間ダイヤモンドを定める |
| `operation.md` | Event Store のバックアップとサービス単位のリプレイ手順を定める |

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| サービス分割 | `settings.gradle` の `include` から**テスト専用サブプロジェクト（`contract-tests`）を除いたもの**が上の 8 つと一致すること |
| サービス間は Axon Server だけ | ビルド：各サービスの本番クラスパスに他サービスの成果物が無いこと。ArchUnit：`RestClient` / `RestTemplate` を `infrastructure/acl` で使わないこと |
| 共有カーネルの範囲 | ArchUnit：`shared` に置けるパッケージの名簿（`domain/model`・`domain/auth`・`contract/*`・`infrastructure/axon`・`infrastructure/time`）を固定する |
| 契約の名簿 | ArchUnit：送信・購読の引数型が `shared/contract` 以外のイベント・コマンド・クエリをサービス越しに使えば赤（契約イベント 11・コマンド 2・クエリ 1） |
| サービス越しの同期状態変更を置かない | ArchUnit：`CommandGateway` の利用箇所を `interfaces`・`application/saga`・`application/reaction` に限定する。`infrastructure/projection` は `CommandGateway` に依存しない |
| 投影がコマンドを送らない | 統合テスト `ReplayIT`：投影の Processing Group をリセットしてリプレイし、`CommandGateway` が 1 度も呼ばれないこと |
| Saga / Reaction は同期クエリを呼ばない | ArchUnit：`application/saga` と `application/reaction` が `QueryGateway` に依存しない |
| ドメイン層のフレームワーク非依存 | ArchUnit：Spring・MyBatis への依存を禁止。Axon は `..annotation..`・`EventAppender`・`org.axonframework.extension.spring.stereotype.EventSourced` の許可リストのみ |
| authms は Event Sourcing にしない | ArchUnit：`auth` パッケージに `@EventSourced` / `@EventSourcedEntity` が無いこと |
| 4 系 API を使わない | ビルド：`org.axonframework.modelling.command.AggregateLifecycle` 等への参照が無いこと（存在しないのでコンパイルで止まる） |
| `axon-server-connector` の接続と DCB | 起動時のヘルスチェック。接続できない、または context が DCB でなければ起動を止める。統合テストで DCB 無効の Axon Server に対して起動が止まることを 1 本固定する |
| スパイクの結果を ADR に戻す | IT1 の DoD：決定 5 の 7 項目それぞれの結果で本 ADR・`architecture_backend.md`・`tech_stack.md` を更新してから IT1 をクローズする |

## 備考

- 著者: claude-code/claude-fable-5-1（分析フェーズ、`orchestrating-analysis` → `analyzing-architecture`）
- 改訂: 2026-09-02 初稿はモジュラーモノリスを提案したが、ユーザーの指示によりマイクロサービスに変更した
- 改訂: 2026-09-02 設計レビュー（`docs/review/cargo-tracker/設計_review_20260902.md` H2）で、決定 3 の集約 API が `take-4` ADR-0007 の形（`@EventSourcedEntity` 単独）のままで、ADR-0008 が実機で退けた経緯（`NoHandlerForCommandException`）を反映していないと指摘された。決定 3 を `@EventSourced(idType, tagKey)` に訂正し、Spring stereotype を 1 つ許す理由と IT1 スパイク第 1 項目を追記した。あわせて決定 2 に ES 適用範囲の理由と見直しの発動条件（M5・H8）、決定 4 に契約の数（H6・M1・M18）、コンプライアンスに DCB（H3）・Reaction Handler（H1）・`contract-tests` の除外（M13）を追加した
- 参照元: `tmp/take-4/docs/adr/0001-axon-framework-adoption.md`、`0004-microservice-decomposition.md`、`0007-axon-5-event-sourcing-api.md`、`0008-axon-5-spring-boot-integration-pattern.md`、`0009-axon-server-connector-explicit-dependency.md`、`0014-shared-module-event-classes.md`
- 参照元: [java-3 ADR-001](../../article/source/java-3/docs/adr/001-microservices-architecture.md)（Event Sourcing 見送りの判断）、[java-3 ADR-022](../../article/source/java-3/docs/adr/022-domain-event-contract.md)
- 記事: [draft-2 アウトライン §5](../../article/practical-ddd-in-enterprise-java/draft-2/outline.md)
- Axon Framework の版は調査時点（2026-09-02）で 5.3 系。確定は `tech_stack.md`
