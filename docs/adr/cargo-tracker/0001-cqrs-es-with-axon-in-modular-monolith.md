---
type: ADR
title: "ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でモジュラーモノリスとして実装する"
description: "CQRS / Event Sourcing を Axon Framework 5 でモジュラーモノリスとして実装する決定。配置の形・ES の適用範囲・Axon 5 系 API の採用と、着手前スパイクで確定する事項。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T02:53:58Z }
---

# ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でモジュラーモノリスとして実装する

国際貨物輸送管理システム（Cargo Tracker）の `java/take-8` を、Axon Framework 5 による CQRS + Event Sourcing で、**単一の Spring Boot アプリケーション（モジュラーモノリス）** として実装する。

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

1. **配置の形**：マイクロサービス（`take-4` を踏襲）か、モジュラーモノリスか
2. **Event Sourcing の適用範囲**：全集約か、一部か
3. **Axon Framework のバージョン**：5 系（API が 4 系と非互換）か、4 系（書籍の参考実装と同じ）か

## 決定

### 1. モジュラーモノリスにする

`take-4` の分割をやめ、1 プロセス・単一 Gradle モジュールにする。BC はパッケージで分け、境界は ArchUnit で守る。

根拠は「**変える軸を 1 つにする**」ことである。第 4 章はプロセス境界が要求するもの（契約テスト・デッドレター・結果整合の可視化）を扱った。第 5 章で同時にプロセスも分けると、Event Sourcing が要求するもの（イベント設計・投影・リプレイ・Upcaster）と混ざり、第 6 章の比較表で「どの代金がどの選択のものか」が言えなくなる。第 3 章と同じ形で永続化と読み書きの分離だけを変えれば、差分がそのまま Event Sourcing の代金になる。

副次的な利点として、Axon Server 1 ノードと PostgreSQL 1 つで動き、Gateway・サービス間認証・サービス別 DB が不要になる。

### 2. Event Sourcing は業務 BC の集約に適用し、Auth と共有カーネルには適用しない

| 適用 | 集約 |
| :--- | :--- |
| する | Booking（`Cargo` / `Shipper` / `Quotation`）、Routing（`Voyage`）、Tracking（`TrackingActivity`）、Handling（`HandlingActivity` / `CustomsDeclaration`）、Billing（`Invoice`） |
| しない | Auth（`User`）：現在状態だけが業務に要る。履歴は監査ログテーブルで足りる |

`take-4` は IT1 で `Shipper` を工数超過の懸念から CRUD に切り替えた経緯がある（`take-4` ADR-0007 のコンテキスト）。本プロジェクトは学習目標を優先し、業務 BC の集約はすべて Event Sourcing にする。工数の問題が出たら、集約単位でこの ADR を更新する。

### 3. Axon Framework 5 系（調査時点 5.3）を採用する

4 系の `@Aggregate` / `@AggregateIdentifier` / `AggregateLifecycle.apply()` / `AggregateTestFixture` は 5 系に存在しない（`take-4` ADR-0007 の検証結果）。本プロジェクトは `take-4` が確定した 5 系のパターンを標準にする。

| 要素 | 採用する API |
| :--- | :--- |
| 集約 | `@EventSourcedEntity(tagKey = "...")`、`@EntityCreator` |
| コマンドハンドラ | `@CommandHandler`（作成系は `static`、更新系はインスタンス）。イベント発行は引数の `EventAppender` |
| 状態復元 | `@EventSourcingHandler` |
| コマンドの宛先 | `@TargetEntityId` |
| 投影 | `@EventHandler` + Processing Group、`pooled-streaming` |
| 問い合わせ | `@QueryHandler` + `QueryGateway` |
| テスト | `AxonTestFixture`（`axon-test`） |

4 系にダウングレードして書籍の参考実装をそのまま使う案は退ける。記事の読者が手にするのは 5 系であり、4 系の API で書いた記事は公開時点で古い。

### 4. 実装着手前にスパイクで確定する事項

`take-4` の ADR が解決していない、または本プロジェクトで条件が変わる事項は IT1 のスパイク（タイムボックス 4h）で確定し、本 ADR を更新する。

| # | 事項 | 背景 |
| :--- | :--- | :--- |
| 1 | Spring Boot 4.1（Jackson 3 既定）と Axon 5.3 の自動設定の整合 | `take-4` は Spring Boot 4 + Jackson 2 で JDBC 自動設定が働かず手動構成した（ADR-0009） |
| 2 | `AxonTestFixture.with(ApplicationConfigurer)` の組み立て方 | `take-4` は Mockito で代替した |
| 3 | Saga のアノテーションと `SagaLifecycle` の 5 系での名称 | `take-4` の設計は 4 系の名称で書かれており実装で未確認 |
| 4 | `axon-server-connector` の明示依存と、無いときのフォールバック検知 | ADR-0009。**無音で in-memory に落ちる**ため、起動時に接続を検査する |

## 影響

### 得るもの

- 全集約の完全な履歴。例外処理（US19・US20・US28）と通関（US29）の「いつ・誰が・何を根拠に」がイベント列から追える
- 読み取りモデルを画面ごとに最適化でき、JOIN に頼らない
- 記事第 5 章の参照元が成立し、第 6 章の 3 アプローチ比較が可能になる

### 払うもの

- イベントが永続化フォーマットになる。フィールドの削除・型変更ができず、Upcaster とゴールデンファイルの契約テストが要る
- 投影は非同期。登録直後に一覧へ出ない状態を画面が扱う
- 投影テーブルは派生データであり、列の追加はマイグレーションでなくリプレイで埋める。リプレイ手順が運用に加わる
- Axon Server というミドルウェアが 1 つ増える（[ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md)）
- Axon 5 系の公開情報が少なく、API の確認にスパイクが要る

### 設計ドキュメントへの波及

| ドキュメント | 内容 |
| :--- | :--- |
| `architecture_backend.md` | 本 ADR の判断を前提に作成済み |
| `domain-model.md` | 集約ごとにコマンド・イベント・状態遷移を定義する。イベントは集約の永続化フォーマットとして設計する |
| `data-model.md` | 投影テーブルと Axon の管理テーブル（`token_entry` / `saga_entry` / `association_value_entry`）を定義する。Event Store のスキーマは Axon Server が持つ |
| `test_strategy.md` | 集約・投影・Saga・イベント契約・ArchUnit の 5 種を定める |
| `operation.md` | Event Store のバックアップとリプレイ手順を定める |

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| 1 プロセス・単一モジュール | `settings.gradle` に `include` が無いことをビルド時に検査する |
| BC の独立 | ArchUnit：BC 間の直接依存は共有カーネルと ACL ポートの実装だけに許す |
| BC 越しの同期状態変更を置かない | ArchUnit：`CommandGateway` の利用箇所を `interfaces` と `application/saga` に限定する |
| ドメイン層のフレームワーク非依存 | ArchUnit：Spring・MyBatis への依存を禁止。Axon は `..annotation..` と `EventAppender` の許可リストのみ |
| Auth は Event Sourcing にしない | ArchUnit：`auth` パッケージに `@EventSourcedEntity` が無いこと |
| 4 系 API を使わない | ビルド：`org.axonframework.modelling.command.AggregateLifecycle` 等への参照が無いこと（存在しないのでコンパイルで止まる） |
| `axon-server-connector` の接続 | 起動時のヘルスチェック。接続できなければ起動を止める |

## 備考

- 著者: claude-code/claude-fable-5-1（分析フェーズ、`orchestrating-analysis` → `analyzing-architecture`）
- 参照元: `tmp/take-4/docs/adr/0001-axon-framework-adoption.md`、`0007-axon-5-event-sourcing-api.md`、`0008-axon-5-spring-boot-integration-pattern.md`、`0009-axon-server-connector-explicit-dependency.md`
- 参照元: [java-3 ADR-001](../../article/source/java-3/docs/adr/001-microservices-architecture.md)（Event Sourcing 見送りの判断）
- 記事: [draft-2 アウトライン §5](../../article/practical-ddd-in-enterprise-java/draft-2/outline.md)
- Axon Framework の版は調査時点（2026-09-02）で 5.3 系。確定は `tech_stack.md`
