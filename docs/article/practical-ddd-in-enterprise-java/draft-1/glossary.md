---
type: Article
title: "エンタープライズ Java における実践的ドメイン駆動設計 — 用語対応表"
description: "アウトラインおよび各章で用いる英語用語と日本語表記の対応表（draft-1）。訳出・併記・原語維持の方針を含む。"
tags: [article, practical-ddd-in-enterprise-java, glossary]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-03T00:00:00Z }
---

# エンタープライズ Java における実践的ドメイン駆動設計 — 用語対応表

本シリーズ（draft-1）で用いる英語用語の日本語表記を定めます。[アウトライン](outline.md)のマインドマップおよび各章の見出し・本文は、この表を正とします。

## 表記方針

| 方針 | 適用対象 | 表記例 |
| :--- | :--- | :--- |
| 訳出 | DDD の概念語・実装手順を表す語 | `Aggregate Class Implementation` → 集約クラスの実装 |
| 併記 | コード上の識別子（パッケージ名など）で、日本語だけでは実体を指せない語 | `interfaces` → インターフェース層（interfaces） |
| 原語維持 | 製品名・フレームワーク名・定着した略語 | `Spring Boot`、`REST API`、`CQRS` |

## 1. DDD の概念

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Problem Space | 問題空間 | 訳出 | |
| Business Domain | ビジネスドメイン | 訳出 | |
| Subdomain | サブドメイン | 訳出 | |
| Bounded Context | 境界づけられたコンテキスト | 訳出 | 略記 BC は本文中でのみ使用 |
| Core Domain | コアドメイン | 訳出 | |
| Ubiquitous Language | ユビキタス言語 | 訳出 | |
| Domain Model | ドメインモデル | 訳出 | |
| Shared Kernel | 共有カーネル | 訳出 | アウトラインの `Shard Kernels` は誤記 |

## 2. ドメインモデルの構成要素

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Aggregate / Aggregates | 集約 | 訳出 | 複数形も「集約」 |
| Aggregate Identifier | 集約識別子 | 訳出 | |
| Entity / Entities | エンティティ | 訳出 | |
| Value Object / Value Objects | 値オブジェクト | 訳出 | |
| Domain Rules | ドメインルール | 訳出 | |
| Command / Commands | コマンド | 訳出 | |
| Query / Queries | クエリ | 訳出 | アウトラインの `QUeries` は誤記 |
| Event / Events | イベント | 訳出 | |
| Domain Event | ドメインイベント | 訳出 | |
| Saga / Sagas | サガ | 訳出 | |
| Projection | 投影 | 訳出 | 読み取りモデルの意。`read model` は「読み取りモデル」 |

## 3. パッケージ構造（層）

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| interfaces | インターフェース層（interfaces） | 併記 | パッケージ名としては原語のまま |
| application | アプリケーション層（application） | 併記 | 同上 |
| domain | ドメイン層（domain） | 併記 | 同上 |
| infrastructure | インフラストラクチャ層（infrastructure） | 併記 | 同上 |
| Packaging | パッケージング | 訳出 | |
| Package Structure | パッケージ構造 | 訳出 | |

## 4. ドメインモデルの実装

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Aggregate Class Implementation | 集約クラスの実装 | 訳出 | |
| Domain Richness | ドメインの豊かさ | 訳出 | 業務知識をモデルに寄せる度合い |
| Domain Richness via Business Attributes | 業務属性によるドメインの豊かさ | 訳出 | 原文末尾の `and finally` は訳出しない |
| State Persistence | 状態の永続化 | 訳出 | |
| Inter-Aggregate References | 集約間の参照 | 訳出 | |
| Entity Class Implementation | エンティティクラスの実装 | 訳出 | |
| Entity-Aggregate Relationships | エンティティと集約の関係 | 訳出 | |
| Entity State Construction | エンティティ状態の構築 | 訳出 | |
| Entity State Persistence | エンティティ状態の永続化 | 訳出 | |
| Value Object Class Implementation | 値オブジェクトクラスの実装 | 訳出 | |
| Value Object-Aggregate Relationship | 値オブジェクトと集約の関係 | 訳出 | |
| Value Object Construction | 値オブジェクトの構築 | 訳出 | |
| Value Object Persistence | 値オブジェクトの永続化 | 訳出 | |
| Implementing Entities/Value Objects | エンティティ／値オブジェクトの実装 | 訳出 | |

## 5. ドメインモデルサービス

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Domain Model Services | ドメインモデルサービス | 訳出 | |
| Inbound Services | 受信サービス | 訳出 | draft-1 本文の既存表記に合わせる |
| Outbound Services | 送信サービス | 訳出 | 同上 |
| Application Services | アプリケーションサービス | 訳出 | |
| Application Services: Command/Query Delegation | アプリケーションサービス：コマンド／クエリの委譲 | 訳出 | |
| Outbound Services: Repository Classes | 送信サービス：リポジトリクラス | 訳出 | |
| Outbound Services: Rest API(s) | 送信サービス：REST API | 併記 | `REST API` は原語維持 |
| Outbound Services: Message Broker | 送信サービス：メッセージブローカー | 訳出 | |
| Event Handlers | イベントハンドラ | 訳出 | 末尾長音は付けない |
| Command Handlers | コマンドハンドラ | 訳出 | 同上 |
| Query Handlers | クエリハンドラ | 訳出 | 同上 |
| Native Web API | ネイティブ Web API | 併記 | |

## 6. CQRS／イベントソーシングと Axon

### 6.1 アウトライン由来の訳語

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Event Sourcing | イベントソーシング | 訳出 | 略記 ES は章タイトルでのみ使用 |
| CQRS | CQRS | 原語維持 | 定着した略語 |
| Event Store | Event Store | 原語維持 | Axon Server の構成要素名。第 5・6 章は原語で統一している |
| State | 状態 | 訳出 | |
| Command Handling | コマンドの処理 | 訳出 | |
| Identification of Commands | コマンドの識別 | 訳出 | |
| Implementation of Commands | コマンドの実装 | 訳出 | |
| Implementation of Command Handlers | コマンドハンドラの実装 | 訳出 | |
| Event Publishing | イベントの発行 | 訳出 | |
| Identification/Implementation of Events | イベントの識別と実装 | 訳出 | |
| Implementation of Event Publishing | イベント発行の実装 | 訳出 | |
| State Maintenance | 状態の維持 | 訳出 | |
| Event Handling within Aggregates | 集約内でのイベント処理 | 訳出 | |
| State Maintenance: The First Command | 状態の維持：最初のコマンド | 訳出 | |
| State Maintenance: Subsequent Commands | 状態の維持：後続のコマンド | 訳出 | |
| Aggregate Projections | 集約の投影 | 訳出 | |
| Identification of Queries | クエリの識別 | 訳出 | |
| Implementation of Queries | クエリの実装 | 訳出 | |
| Implementation of Query Handlers | クエリハンドラの実装 | 訳出 | |
| Axon Components | Axon コンポーネント | 併記 | `Axon` は原語維持 |
| Axon Dispatch Model Components | Axon のディスパッチモデルコンポーネント | 併記 | |
| Command Bus | コマンドバス | 訳出 | |
| Query Bus | クエリバス | 訳出 | |
| Event Bus | イベントバス | 訳出 | |

### 6.2 Axon 5 のアノテーションと型

第 5 章が転記するコードに現れる識別子です。**いずれも原語維持**します——コード上の名前であり、訳すと実体を指せません。本文では初出で役割を補足します。

| 識別子 | 役割 | 初出での補足 |
| :--- | :--- | :--- |
| `@EventSourced(idType, tagKey)` | 集約をイベントソーシングの対象として登録する | ドメイン層が持つ唯一の Spring stereotype |
| `@EntityCreator` | イベント再生の起点となる空の集約を用意する | |
| `@CommandHandler` | コマンドを受けて業務判断を行う | 集約のメソッドに付く |
| `@EventSourcingHandler` | イベントを状態へ反映する | **業務判断を書かない** |
| `@EventHandler` | 投影または連鎖の調整でイベントを受ける | 置き場で役割が決まる |
| `@QueryHandler` | 問い合わせに投影テーブルから答える | |
| `@TargetEntityId` | コマンドの宛先となる集約を特定する | コマンドの `record` に付く |
| `@EventTag(key)` | イベントを集約に結び付けるタグを書く | **付け忘れると集約が空のまま復元される** |
| `@SequencingPolicy` | イベント処理の列の切り方を決める | 列の切り方が退避の被害範囲になる |
| `EventAppender` | イベントを追記する（Axon 4 の `AggregateLifecycle.apply()` に相当） | コマンドハンドラの引数で受ける |
| `CommandGateway` / `QueryGateway` | コマンド・問い合わせの送り口 | Controller と Reaction Handler が使う |
| `AxonTestFixture` | 集約のテスト用フィクスチャ | `disableAxonServer()` ではタグ復元が働かない |

### 6.3 ランタイムと運用の用語

| 英語表記 | 日本語表記 | 方針 | 備考 |
| :--- | :--- | :--- | :--- |
| Projection | 投影 | 訳出 | §2 と同じ。捨てて作り直せるもので、正典ではない |
| Read Model | 読み取りモデル | 訳出 | 投影テーブルを通じて答えるモデル |
| Eventual Consistency | 結果整合 | 訳出 | 「結果整合性」とは書かない |
| Replay | リプレイ | 訳出 | 投影を作り直すこと。「再生」はイベントから状態を復元する意で使い分ける |
| Upcaster | Upcaster | 原語維持 | 旧形式のイベントを新しい形へ読み替える仕組み。訳語が定着していない |
| Processing Group | Processing Group | 原語維持 | Axon 5 ではパッケージ名で指定する（`@ProcessingGroup` は存在しない） |
| Dead Letter Queue / DLQ | 退避（デッドレター） | 併記 | 処理できなかったイベントの置き場。第 4 章の「デッドレター」と同じもの |
| Token Store | `TokenStore` | 原語維持 | どこまで読んだかを持つ。自動設定されない |
| Reaction Handler | Reaction Handler | 原語維持 | Axon 5 に Saga が無いため、その役割を担うイベントハンドラ。訳語を当てると「サガ」と混同する |
| Dynamic Consistency Boundary / DCB | DCB | 原語維持 | タグで集約を復元する Axon Server の仕組み |
| Compensation | 補償 | 訳出 | 取り消しではなく「やり直せる状態に戻す」こと |

### 6.4 Axon 4 にあって 5 に無いもの

読者が Axon 4 の知識で本文を読むと食い違う箇所です。**第 5 章の実装はいずれも使っていません。**

| Axon 4 | Axon 5 での扱い |
| :--- | :--- |
| `@Aggregate` / `@AggregateIdentifier` | `@EventSourced(idType, tagKey)` に置き換わった |
| `AggregateLifecycle.apply()` | 引数で受ける `EventAppender` に置き換わった |
| `AggregateTestFixture` | `AxonTestFixture.with(ApplicationConfigurer)` に置き換わった |
| `@Saga` / `SagaLifecycle` / Saga Store | **存在しない。** Reaction Handler + `process_state` で置き換える |
| `@ProcessingGroup` | **存在しない。** パッケージ名をキーに設定で指定する |
| Deadline（タイムアウト起点の処理） | **存在しない。** 投影テーブルを定期に走査する運用ジョブにする |

## 7. 原語維持する固有名詞・略語

| 英語表記 | 表記 | 備考 |
| :--- | :--- | :--- |
| Cargo Tracker | Cargo Tracker | 題材アプリケーション名 |
| Spring Framework | Spring Framework | |
| Spring Boot | Spring Boot | |
| Spring Cloud | Spring Cloud | アウトラインの `Spring Claud` は誤記 |
| Spring Platform | Spring プラットフォーム | 「プラットフォーム」のみ訳出 |
| Axon Framework | Axon Framework | |
| Axon Server | Axon Server | 製品名のため訳出しない |
| Axon Framework 5 | Axon 5 | 本文では「Axon 5」と略す。4 系とは API が非互換（§6.4） |
| REST API / RESTful API | REST API / RESTful API | |
| EDA | EDA | Event-Driven Architecture。初出で「イベント駆動アーキテクチャ（EDA）」と補足 |
| DDD | DDD | Domain-Driven Design |

## 8. アウトライン上の誤記と修正

| 誤記 | 正 |
| :--- | :--- |
| `Shard Kernels` | 共有カーネル（Shared Kernel） |
| `QUeries` | クエリ（Queries） |
| `Spring Claud` | Spring Cloud |
