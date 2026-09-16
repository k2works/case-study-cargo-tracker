---
type: ADR
title: "ADR-0002 Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする"
description: "Event Store は Axon Server SE 単一ノード、Read Model・Token Store・Saga Store・Auth は PostgreSQL + MyBatis + Flyway に置く決定と、PostgresqlEventStorageEngine 公開時の再評価条件。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-02T13:24:08Z }
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
---

# ADR-0002 Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする

イベント列の永続化とサービス間の Command / Event / Query Bus には **Axon Server Standard Edition（単一ノード）** を使い、投影テーブル・Token Store・Saga Store・Auth の状態は **サービスごとの PostgreSQL 16 + MyBatis 3 + Flyway** に置く（Database per Service）。

日付: 2026-09-02

## ステータス

2026-09-02 提案されました

## コンテキスト

[ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) で Axon Framework 5 による Event Sourcing を決めた。イベントをどこに書くかと、読み取りモデルをどう持つかを決める必要がある。

Axon Framework 5 の Event Storage Engine には、調査時点（2026-09-02）で次の選択肢がある（Axon Framework 5.2 リファレンス「Event Store Migration」より）。

| Engine | DCB 対応 | 備考 |
| :--- | :--- | :--- |
| `AxonServerEventStorageEngine` | あり | Axon Server 2025.2.0 以降 |
| `AggregateBasedJpaEventStorageEngine` | なし | JPA が要る。参照元 2 つが JPA を退けている |
| `PostgresqlEventStorageEngine` | あり | 「2026 Q1 公開予定」と記載。公開状況は未確認 |
| `InMemoryEventStorageEngine` | なし | テスト専用 |
| `JdbcEventStorageEngine` | — | 5 系に後継なし |

DCB（Dynamic Consistency Boundary）は `@EventSourced(tagKey = ...)` の前提であり、DCB 非対応のエンジンでは `take-4` ADR-0007 / 0008 が確定した集約のパターンをそのまま使えない。Axon Server 側でも context を DCB 形式で作る必要があり（standalone は `AXONIQ_AXONSERVER_STANDALONE_DCB=true`、クラスタは `dcb=true`）、無いと接続が確立しない（IT1 スパイクの実測は `AXONIQ-1302 default: not found in any replication group`。アプリケーションは起動を止めず無限再接続する）。

読み取り側については、参照元 2 つ（`java-2` ADR-004、`take-4` ADR-0002）がいずれも MyBatis を採用し、JPA を退けている。

## 決定

### Event Store とメッセージバス：Axon Server SE 単一ノード

理由は 4 つある。

1. **DCB 対応で、`take-4` が実機検証した構成**である。ADR-0008・0009 が見つけた落とし穴（`subscribing` への逃げ、connector の欠落）と対処が既知
2. マイクロサービス構成（ADR-0001）ではサービス間の Command / Event / Query の配送が要る。Axon Server はそれを Event Store と一体で提供し、RabbitMQ 等を別に立てなくてよい
3. `PostgresqlEventStorageEngine` は DCB 対応の唯一の RDB 案だが、公開状況を確認できていない。公開されても分散バスの役割は残る
4. JPA を持ち込む案は、参照元との一貫性（MyBatis）を崩す

Enterprise Edition は採らない。単一ノードで学習目標を満たし、可用性は `non_functional.md` で要件化してから再評価する。再評価の発動条件は「**計画停止を除いた実績が 3 か月連続で SLO（可用性 99.9%）を割ったら**」とする。単一ノードは月 1 回 10 分の計画停止だけで SLO の余裕をほぼ使い切るため、計画停止を含めて測ると常に再評価が発動してしまう。

### Read Model・管理テーブル：サービスごとの PostgreSQL + MyBatis + Flyway

サービスごとに DB を分ける（`auth_db` / `booking_read_db` / `routing_read_db` / `tracking_read_db` / `handling_read_db` / `billing_read_db`）。サービスは他サービスの DB に接続しない。

| テーブル群 | 内容 |
| :--- | :--- |
| 投影テーブル | サービスの DB に置く（`cargo_summary` など）。派生データであり、いつでも捨ててリプレイで再構築できる |
| `token_entry` | Processing Group の処理位置。投影の更新と同一 JDBC トランザクションで書く |
| `saga_entry` / `association_value_entry` | Saga の状態 |
| `users` / `roles` / `auth_audit_log` | Auth の状態保存 |

Token Store と Saga Store を Axon Server ではなく PostgreSQL に置くのは、投影の更新とトークンの更新を 1 トランザクションにするためである。分けると「投影は書けたがトークンは進まない」窓ができ、同じイベントが 2 度投影される。

### ローカル開発でも Axon Server を立てる

`take-4` ADR-0008 は H2 プロファイルで `subscribing` モードに逃げ、投影が同期実行される構成を一時的に許した。その結果、Axon Server を止めても正常応答する構成不全に気づくのが遅れた（ADR-0009）。本プロジェクトは kind + Kustomize（`ops/k8s/base/axonserver/`）で Axon Server SE と PostgreSQL を常に立て、**プロファイルによる Event Processor のモード切り替えをしない**。テストも Testcontainers で同じ構成（DCB 有効）を使う。

### `shared` の変更は全サービスの再ビルド

契約イベント・コマンド・クエリと Axon の共通設定は `shared` に置く（ADR-0001）。`shared` を上げる PR は業務 5 サービス全部を再ビルドすることになり、片方だけ古い契約で動く窓ができる。**`shared` を上げる PR は全サービスの往復テスト（Axon Server を実際に経由する契約テスト）を通すことを品質ゲートにする。**

### 再評価の条件

`PostgresqlEventStorageEngine` が Maven Central に公開され、DCB 対応が確認できた時点で、Event Store を各サービスの PostgreSQL に置く案を本 ADR の後継として検討する。ただしサービス間の配送には Axon Server（または別のバス）が引き続き要るため、外せるのは Event Store の役割だけである。IT1 のスパイクで公開状況を確認する。

## 影響

- ローカル・ステージング・本番に Axon Server のコンテナが 1 つ増え、全サービスの単一障害点になる。バックアップ対象が Event Store（Axon Server）と PostgreSQL × 6 になる
- Event Store は追記専用のため、バックアップは「イベントの欠落が無いこと」を検証する。復元手順は `operation.md` で定める
- 投影テーブルの変更はマイグレーションでなくリプレイで反映する。リプレイの Gulp タスクと手順書が要る
- `axon-server-connector` を `build.gradle` に明示依存として書く。無いと無音で in-memory に落ちる（`take-4` ADR-0009）

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| Axon Server に接続していること | 起動時に接続を確認し、失敗したら起動を止める。統合テストで「Axon Server 停止時に起動しない」ことを 1 本固定する |
| context が DCB であること | 起動時の接続検査に含める。統合テストで DCB 無効の Axon Server（`STANDALONE_DCB` 未設定）に対して起動が止まることを 1 本固定する。Testcontainers・kind・ECS の Axon Server 定義に `AXONIQ_AXONSERVER_STANDALONE_DCB=true` があることを設定ファイルの走査で検査する |
| ローカルでも Axon Server を立てる | `ops/k8s/base/axonserver/` が存在し、kind の Kustomize に含まれていることを検査する。`docker-compose.yml` に Axon Server を置かない（ローカルは kind） |
| Axon Server に接続するのは業務 5 サービスだけ | 設定ファイルの走査：authms・gatewayms に `axon.axonserver` が無いこと。監視の期待接続数は「5 × 台数」、上限はサービスあたり 50・合計 250 |
| `shared` を上げる PR は全サービスの往復テストを通す | CI：`shared/**` に差分があるとき、業務 5 サービスの契約往復テスト（`contract-tests`）を必ず実行する |
| JPA を使わない | ビルド：本番実行クラスパスに `jakarta.persistence` と Hibernate が無いこと（`java-2` の `verifyProductionDependencies` を移植） |
| モード切り替えをしない | 設定ファイルの走査：`axon.eventhandling.processors.*.mode` が `pooled` 以外にならないこと。全 Processing Group（投影 8 + Reaction 3）が明示列挙されていること（列挙漏れが赤） |
| 投影とトークンが同一トランザクション（構成） | 起動時の検査：Axon の `TransactionManager` Bean が 1 つだけで、`SpringTransactionManager` が `ConnectionProvider` 付きで作られていること。`token_entry` に `mask INTEGER NOT NULL` があること（Flyway の DDL 検査） |
| 投影とトークンが同一トランザクション | 統合テスト：投影の SQL を故意に失敗させ、トークンが進まないことを固定する |
| 投影が再構築できること | 統合テスト：投影テーブルを空にしてトークンをリセットし、リプレイ後に同じ行が復元されること |
| Database per Service | 設定ファイルの走査：各サービスの `spring.datasource.url` が自サービスの DB だけを指すこと |

## 備考

- 著者: claude-code/claude-fable-5-1
- 改訂: 2026-09-02 設計レビュー（H3・M2・M4・アーキテクトの懸念事項）を反映。DCB の接続検査、EE 再評価の発動条件、ローカル Axon Server の存在検査、`shared` を上げる PR の品質ゲートを追加した
- 参照: Axon Framework 5.2 リファレンス「Event Store Migration」<https://docs.axoniq.io/axon-framework-reference/5.2/migration/paths/event-store/>
- 参照元: `tmp/take-4/docs/adr/0002-mybatis-adoption.md`、`0008-axon-5-spring-boot-integration-pattern.md`、`0009-axon-server-connector-explicit-dependency.md`
- 関連: [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md)
