---
type: ADR
title: "ADR-0002 Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする"
description: "Event Store は Axon Server SE 単一ノード、Read Model・Token Store・Saga Store・Auth は PostgreSQL + MyBatis + Flyway に置く決定と、PostgresqlEventStorageEngine 公開時の再評価条件。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T02:53:58Z }
---

# ADR-0002 Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする

イベント列の永続化には **Axon Server Standard Edition（単一ノード）** を使い、投影テーブル・Token Store・Saga Store・Auth の状態は **PostgreSQL 16 + MyBatis 3 + Flyway** に置く。

日付: 2026-09-02

## ステータス

2026-09-02 提案されました

## コンテキスト

[ADR-0001](0001-cqrs-es-with-axon-in-modular-monolith.md) で Axon Framework 5 による Event Sourcing を決めた。イベントをどこに書くかと、読み取りモデルをどう持つかを決める必要がある。

Axon Framework 5 の Event Storage Engine には、調査時点（2026-09-02）で次の選択肢がある（Axon Framework 5.2 リファレンス「Event Store Migration」より）。

| Engine | DCB 対応 | 備考 |
| :--- | :--- | :--- |
| `AxonServerEventStorageEngine` | あり | Axon Server 2025.2.0 以降 |
| `AggregateBasedJpaEventStorageEngine` | なし | JPA が要る。参照元 2 つが JPA を退けている |
| `PostgresqlEventStorageEngine` | あり | 「2026 Q1 公開予定」と記載。公開状況は未確認 |
| `InMemoryEventStorageEngine` | なし | テスト専用 |
| `JdbcEventStorageEngine` | — | 5 系に後継なし |

DCB（Dynamic Consistency Boundary）は `@EventSourcedEntity(tagKey = ...)` の前提であり、DCB 非対応のエンジンでは `take-4` ADR-0007 が確定した集約のパターンをそのまま使えない。

読み取り側については、参照元 2 つ（`java-2` ADR-004、`take-4` ADR-0002）がいずれも MyBatis を採用し、JPA を退けている。

## 決定

### Event Store：Axon Server SE 単一ノード

理由は 3 つある。

1. **DCB 対応で、`take-4` が実機検証した構成**である。ADR-0008・0009 が見つけた落とし穴（`subscribing` への逃げ、connector の欠落）と対処が既知
2. `PostgresqlEventStorageEngine` は DCB 対応の唯一の RDB 案だが、公開状況を確認できていない
3. JPA を持ち込む案は、参照元との一貫性（MyBatis）を崩す

Enterprise Edition は採らない。単一ノードで学習目標を満たし、可用性は `non_functional.md` で要件化してから再評価する。

### Read Model・管理テーブル：PostgreSQL + MyBatis + Flyway

| テーブル群 | 内容 |
| :--- | :--- |
| 投影テーブル | BC ごとに接頭辞（`booking_cargo_summary` など）。派生データであり、いつでも捨ててリプレイで再構築できる |
| `token_entry` | Processing Group の処理位置。投影の更新と同一 JDBC トランザクションで書く |
| `saga_entry` / `association_value_entry` | Saga の状態 |
| `users` / `roles` / `auth_audit_log` | Auth の状態保存 |

Token Store と Saga Store を Axon Server ではなく PostgreSQL に置くのは、投影の更新とトークンの更新を 1 トランザクションにするためである。分けると「投影は書けたがトークンは進まない」窓ができ、同じイベントが 2 度投影される。

### ローカル開発でも Axon Server を立てる

`take-4` ADR-0008 は H2 プロファイルで `subscribing` モードに逃げ、投影が同期実行される構成を一時的に許した。その結果、Axon Server を止めても正常応答する構成不全に気づくのが遅れた（ADR-0009）。本プロジェクトは Docker Compose で Axon Server SE と PostgreSQL を常に立て、**プロファイルによる Event Processor のモード切り替えをしない**。テストも Testcontainers で同じ構成を使う。

### 再評価の条件

`PostgresqlEventStorageEngine` が Maven Central に公開され、DCB 対応が確認できた時点で、Axon Server を外して PostgreSQL 1 つに寄せる案を本 ADR の後継として起こす。IT1 のスパイクで公開状況を確認する。

## 影響

- ローカル・ステージング・本番に Axon Server のコンテナが 1 つ増える。バックアップ対象が Event Store（Axon Server）と PostgreSQL の 2 つになる
- Event Store は追記専用のため、バックアップは「イベントの欠落が無いこと」を検証する。復元手順は `operation.md` で定める
- 投影テーブルの変更はマイグレーションでなくリプレイで反映する。リプレイの Gulp タスクと手順書が要る
- `axon-server-connector` を `build.gradle` に明示依存として書く。無いと無音で in-memory に落ちる（`take-4` ADR-0009）

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| Axon Server に接続していること | 起動時に接続を確認し、失敗したら起動を止める。統合テストで「Axon Server 停止時に起動しない」ことを 1 本固定する |
| JPA を使わない | ビルド：本番実行クラスパスに `jakarta.persistence` と Hibernate が無いこと（`java-2` の `verifyProductionDependencies` を移植） |
| モード切り替えをしない | 設定ファイルの走査：`axon.eventhandling.processors.*.mode` が `pooled` 以外にならないこと |
| 投影とトークンが同一トランザクション | 統合テスト：投影の SQL を故意に失敗させ、トークンが進まないことを固定する |
| 投影が再構築できること | 統合テスト：投影テーブルを空にしてトークンをリセットし、リプレイ後に同じ行が復元されること |

## 備考

- 著者: claude-code/claude-fable-5-1
- 参照: Axon Framework 5.2 リファレンス「Event Store Migration」<https://docs.axoniq.io/axon-framework-reference/5.2/migration/paths/event-store/>
- 参照元: `tmp/take-4/docs/adr/0002-mybatis-adoption.md`、`0008-axon-5-spring-boot-integration-pattern.md`、`0009-axon-server-connector-explicit-dependency.md`
- 関連: [ADR-0001](0001-cqrs-es-with-axon-in-modular-monolith.md)
