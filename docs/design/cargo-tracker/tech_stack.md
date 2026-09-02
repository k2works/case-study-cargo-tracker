---
type: Design
title: "技術スタック - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）"
description: "CQRS / Event Sourcing 版 Cargo Tracker の技術スタック一覧（調査時点 2026-09-02）。Java 25 / Spring Boot 4.1 / Axon Framework 5.1.0-RC2 / Axon Server 2026.0.4 / MyBatis / PostgreSQL 16 / React 19 と、IT1 スパイクの結果、採用しないもの、バージョン管理方針。"
tags: [design,tech-stack,axon,spring-boot,react]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-02T13:24:08Z }
stale_after: 2026-12-01T00:00:00Z
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
  - { by: human:kakimomokuri, at: 2026-09-02T12:47:29Z }
---

# 技術スタック - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）

## 概要

[バックエンド](architecture_backend.md)・[フロントエンド](architecture_frontend.md)・[インフラ](architecture_infrastructure.md)の各アーキテクチャに基づく技術スタックの一覧です。バージョンは **調査時点（2026-09-02）** の値であり、実装着手時に再確認します。本書には 90 日の見直し期限を付けます。

**本書が正典です。** 他の設計文書に書かれたバージョンと食い違う場合は本書に従います。

| 参照元 | 採るもの | 変えるもの |
| :--- | :--- | :--- |
| `tmp/take-4/docs/design/tech_stack.md` | Java 25 / Spring Boot 4 / Axon 5 / Axon Server 2026 / MyBatis / PostgreSQL 16 / Gradle 9 の組み合わせ、バージョン実在性のチェックリスト、LTS 追随ルール | H2・RestTemplate・Spring Cloud Contract を外す。Axon 系は 5.1.0-RC2 に固定し（IT1 スパイク：connector が 5.2 以降に無い）、Jackson 3 を前提にする |
| `docs/article/source/java-3/docs/design/tech_stack.md` | フロントエンド（React 19 / Vite / TanStack Query / Zustand / Tailwind）、kind + Kustomize、ECS + RDS | RabbitMQ / Spring Cloud Stream / CloudAMQP / Amazon MQ を外す |

## 実装着手前の確認（IT1 スパイク）

バージョンは Maven Central と Docker Hub で実在を確認してから `libs.versions.toml` に固定します。次は本書だけでは確定できなかったため、IT1 のスパイクで確かめた項目です（[ADR-0001](../../adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md) の決定 5）。実施日 2026-09-02。

| # | 確認事項 | 結果 |
| :--- | :--- | :--- |
| 1 | **集約の登録 API**：`@EventSourcedEntity` 単独（Spring stereotype 無し）で集約が Command Bus に登録されるか | **登録されない**（`@EventSourcedEntity` が 5.1.0-RC2 に存在しない）。`@EventSourced(idType, tagKey)` を標準とし ArchUnit の許可リストに入れる。bootJar・実 Axon Server・`CommandGateway` のモック無しで、コマンド受理 → イベント保存 → 投影受信まで到達することを確認した |
| 2 | starter・`axon-server-connector`・`axon-test` が **同じバージョン**で揃うこと | **5.3 系では揃わない。** connector は 5.0.0 と 5.1.0-RC2 しか公開されていない。コア 5.3.1 + connector 5.1.0-RC2 は `CommandBusConnector` / `QueryBusConnector` / `AxonServerConfigurationEnhancer` を解決できず Axon Server に接続できない（jar のリンク検査で実測）。**全 Axon を 5.1.0-RC2 に固定する** |
| 3 | Spring Boot 4.1（Jackson 3 既定）で Axon の自動設定が働くこと | **Spring Boot 4.1.1 + Java 25 で起動する**が、`spring.main.allow-circular-references=true` と**明示的な `TokenStore` Bean** が必要（`TokenStore` が無いと `Could not find a mandatory TokenStore` で起動失敗）。`TransactionManager` の重複・`SpringTransactionManager` の第 3 引数・`token_entry.mask` は DB を伴う IT1 タスク 1.3 で確認する |
| 4 | 起動時の接続検査が DCB 無効の context を赤にすること | **既定では赤にならない。** DCB 無効の context に繋ぐと `AXONIQ-1302 default: not found in any replication group` が出るが、**起動は止まらず無限に再接続を試み続ける**。設計が想定した `AXONIQ-2308` は 2026.0.4 では出ない。検査はログ検出でなく context への問い合わせで実装する（IT1 タスク 1.4） |
| 5 | `AxonTestFixture.with(...)` の組み立て方 | **`AxonTestFixture.with(ApplicationConfigurer)`**。あわせて `axon-test` に `org.axonframework.test.server.AxonServerContainer`（Testcontainers）が同梱されていることが分かった。IT1 タスク 2.4 はこれを使う |
| 6 | Saga のアノテーション名（5 系での名称） | **Axon 5 に Saga は無い。** 5.0.0・5.1.0-RC2・5.3.1 のどの jar にも `Saga`・`Deadline`・`@ProcessingGroup` を含むクラスが存在しない。調整役は Reaction Handler で実装する（ADR-0001 決定 6）。Processing Group は `axon.eventhandling.processors."[<パッケージ名>]"` のパッケージキーで指定する |
| 7 | サービス越しの Command / Query が届くこと | **届く。** 集約を持たない JVM が送ったコマンドを、集約を持つ別 JVM が処理し投影まで到達することを 2 JVM で確認した |
| 8 | `PostgresqlEventStorageEngine` の公開状況 | **未確認**（ADR-0002 の再評価条件のまま。IT2 以降） |
| 9 | S3 へエクスポートした Event Store からの**差分再投入**が可能か | **未実施。** #1〜#7 で版の前提が崩れ、その確定に時間を使った。RPO の根拠が未検証のまま残るため IT2 のリスクとして持ち越す |

## バックエンド

### 言語・ランタイム

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| Java | 25（LTS） | 開発言語 | 2033-09 | Axon 5 / Spring Boot 4 の要求（17 以上）を満たす最新 LTS。`record`・パターンマッチ・Virtual Threads |
| Eclipse Temurin | 25 | JVM・コンテナイメージ | 2033-09 | `eclipse-temurin:25-jre-alpine` |

### フレームワーク

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| Spring Boot | **4.1.1**（固定） | サービスの基盤 | OSS サポート 4.1 系は 2027 年前半（確認要） | Spring Framework 7、Jackson 3 既定。**全サービスで `spring.main.allow-circular-references=true` が必要**（Axon の `axon.axonserver` ConfigurationProperties が Bean 循環を作る。Boot 4.0.6 でも同じ。IT1 スパイク） |
| **Axon Framework** | **5.1.0-RC2**（固定） | CQRS / Event Sourcing / 分散バス（**Saga は無い**） | コミュニティ | [ADR-0001](../../adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)。5 系の Entity API（`@EntityCreator` / `EventAppender` / `@EventSourcingHandler`）。集約の登録は `@EventSourced(idType, tagKey)`（Spring stereotype。IT1 スパイクで実機確認済み）。**5.3 系は採らない**：`axon-server-connector` が 5.2 以降 Maven Central に無く、コアだけ上げると `CommandBusConnector` / `QueryBusConnector` を欠いて Axon Server に接続できない |
| `axon-spring-boot-starter` | Axon と同じ | Spring Boot 統合 | — | `org.axonframework.extensions.spring`。`@EventSourced` はこの starter が提供する（`axon-spring`） |
| `axon-server-connector` | Axon と同じ（5.1.0-RC2） | Axon Server への gRPC 接続 | — | **明示依存**。starter の推移的依存に含まれない（5.1・5.3 とも確認）。**この成果物が版の上限を決める**（公開は 5.0.0 と 5.1.0-RC2 のみ）。起動時に接続と **DCB context** を検査する（既定では DCB 無効でも起動が止まらず `AXONIQ-1302` で無限再試行する） |
| **Axon Server** | **2026.0.4**（Standard Edition） | Event Store / Command Bus / Event Bus / Query Bus | AxonIQ | [ADR-0002](../../adr/cargo-tracker/0002-event-store-axon-server-and-postgresql-read-models.md)。Docker `axoniq/axonserver:2026.0.4`（SE / EE は同一イメージ、ライセンスの有無で切り替わる）。`AXONIQ_AXONSERVER_STANDALONE_DCB=true` が必須（クラスタは `dcb=true`） |
| Spring Cloud Gateway | Spring Boot 4.1 対応版 | ルーティング・JWT 検証・CORS・レート制限 | — | `gatewayms` |
| Spring Security | 7 系 | 認証・認可 | — | Spring Boot 4 と整合 |
| jjwt | 0.12 系 | JWT 発行・検証 | — | `authms` と `shared` |
| Jackson | 3 系（Spring Boot 4 同梱） | イベントのシリアライズ | — | Axon のシリアライザ。IT1 で Axon との整合を確認 |
| Micrometer Tracing | Spring Boot 同梱 | サービスをまたぐ相関 | — | イベントのメタデータに `traceId` |
| Resilience4j | 2 系 | Reaction Handler の再試行・Query Bus 呼び出しのタイムアウト（既定 5 秒） | — | Reaction Handler から同期クエリを呼ばない |

### データアクセス（投影・Auth）

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16 系 | 投影 DB × 5、`auth_db` | 2028-11 | 参照元と同じ。RDS で Multi-AZ |
| MyBatis | 3.5 系 | 投影の書き込み・Query Handler の SQL | — | SQL を自分で持つ。JPA は採らない |
| `mybatis-spring-boot-starter` | 3 系（Spring Boot 4 対応版） | Spring 統合 | — | |
| Flyway | 11 系 | サービスごとのマイグレーション | — | `V<num>__<desc>.sql`。適用済みは編集しない |
| HikariCP | Spring Boot 同梱 | 接続プール | — | |
| Axon `JdbcTokenStore` | Axon 同梱 | Token の永続化（**`JdbcSagaStore` は Axon 5 に無い**） | — | 投影と同一 DataSource・同一トランザクション。**`TokenStore` Bean は自動設定されないので明示的に登録する**（無いと起動失敗。IT1 スパイク）。`TransactionManager` Bean は 1 つ、`token_entry.mask INTEGER NOT NULL` |
| ~~H2~~ | 採らない | — | — | 方言差の検査は実 DB で行う。Testcontainers を使う |

### ビルド・API

| 技術 | バージョン | 用途 | 選定理由 |
| :--- | :--- | :--- | :--- |
| Gradle | 9 系（Wrapper 同梱） | マルチプロジェクトのビルド | `libs.versions.toml` でバージョンを一元管理 |
| springdoc-openapi | Spring Boot 4 対応版（3 系） | OpenAPI と Swagger UI | フロントエンドの型生成 |
| ~~RestTemplate / RestClient~~ | サービス間では採らない | — | サービス間は Axon Query Bus。ArchUnit で禁止 |

### テスト（バックエンド）

| 技術 | バージョン | 用途 |
| :--- | :--- | :--- |
| JUnit | 5.11 以上 | テストフレームワーク |
| AssertJ | 3.26 以上 | アサーション |
| Mockito | 5 系 | モック（ドメイン外の依存に限る） |
| `axon-test` | Axon と同じ | `AxonTestFixture.with(ApplicationConfigurer)`（集約）。統合テスト用の `AxonServerContainer`（Testcontainers）も同梱する |
| Testcontainers | 1.20 以上 | PostgreSQL・Axon Server の起動 |
| Awaitility | 4 系 | 投影の反映待ち。受け入れテストの「N 秒以内に」ステップもこれに閉じる |
| **Cucumber JVM** | **7.34 系** | 受け入れテスト（デモ項目の Gherkin 実行）。`cucumber-java`・`cucumber-junit-platform-engine`・`cucumber-spring` を**同一バージョンで揃える** |
| REST Assured | 5 系 | 受け入れテストのステップ定義から Gateway 経由の REST を叩く |
| ArchUnit | 1.4 以上 | レイヤー・共有カーネル・契約の名簿 |
| JaCoCo | Gradle プラグイン | レイヤー別カバレッジ閾値 |
| SpotBugs | Gradle プラグイン | `./gradlew build` に含める |
| ~~Spring Cloud Contract~~ | 採らない | 契約はゴールデン JSON と Axon Server 経由の往復で守る |
| ~~WireMock~~ | 採らない | REST の外部連携が無い |

## フロントエンド

| 技術 | バージョン | 用途 | 選定理由 |
| :--- | :--- | :--- | :--- |
| TypeScript | 5 系 | 言語 | |
| React | 19 系 | UI | 参照元と同じ |
| Vite | 6 系 | ビルド・開発サーバー | |
| React Router | 7 系 | ルーティング・認可ガード | |
| TanStack Query | 5 系 | サーバー状態、`202` のポーリング、`invalidateQueries` | 結果整合の吸収 |
| Zustand | 5 系 | 認証ストア（`sessionStorage`） | |
| React Hook Form + Zod | 7 系 / 3 系 | フォームと検証 | |
| Tailwind CSS | 4 系 | スタイル | |
| shadcn/ui | — | コンポーネント（コピーして使う） | 依存に持たない |
| openapi-typescript | 7 系 | API 型の生成 | |
| Vitest + Testing Library | 3 系 / 16 系 | ユニット・統合 | |
| MSW | 2 系 | API モック（本物より甘くしない） | |
| Playwright | 1.5x | E2E（到達性・反映中・409） | |
| ESLint（`import/no-restricted-paths`） | 9 系 | フィーチャー間依存の禁止 | |

## インフラ

| 区分 | 技術 | バージョン | 用途 |
| :--- | :--- | :--- | :--- |
| コンテナ | Docker | 27 以上 | イメージビルド |
| ローカル | kind + Kustomize | kind 0.2x / kubectl 同梱 | ローカルの全サービス + Axon Server + PostgreSQL |
| ステージング・本番 | AWS ECS（Fargate + EC2 起動タイプ） | — | サービスは Fargate、Axon Server は EC2 + EBS |
| DB | Amazon RDS for PostgreSQL | 16 | 1 インスタンス 6 DB、Multi-AZ |
| ネットワーク | ALB、VPC、ECS Service Connect | — | |
| バックアップ | AWS Backup、Lambda、S3、Glacier | — | EBS スナップショット 1 時間、Event Store のエクスポート |
| シークレット・鍵 | Secrets Manager、KMS | — | 荷主ごとの暗号化鍵（crypto-shredding） |
| IaC | Terraform | 1.9 以上 | |
| CI/CD | GitHub Actions（OIDC）、GHCR | — | |
| 監視 | CloudWatch、Micrometer、OpenTelemetry | — | Event Processor の遅れをカスタムメトリクスで |
| 品質 | SonarQube | Community | Quality Gate |
| 運用スクリプト | Node.js + Gulp | 22 LTS / 5 系 | `projection:replay` ほか |
| ドキュメント | MkDocs Material、PlantUML | — | OKF バンドル |

## 採用しないもの

| 技術 | 理由 |
| :--- | :--- |
| Axon Framework 4 系 | 5 系と API が非互換。記事の読者が手にするのは 5 系 |
| Axon Server Enterprise Edition | 単一ノードで学習目標を満たす。再評価条件は `non_functional.md` |
| JPA / Hibernate | 参照元 2 つが退けた。投影は MyBatis の SQL |
| H2 | 方言差の検査を実 DB で行う |
| RabbitMQ / Kafka / Spring Cloud Stream | Axon Server がバスを兼ねる |
| Heroku | Axon Server を運用できない |
| Docker Compose | ローカルもステージングと同じ Service 名解決にする |
| SSE / WebSocket | 要件に無い |

## バージョン管理方針

| 方針 | 内容 |
| :--- | :--- |
| 固定 | `libs.versions.toml` と `package.json` で完全一致のバージョンを固定。範囲指定をしない |
| Axon 系の同期 | `axon-spring-boot-starter` / `axon-server-connector` / `axon-test` は必ず同じバージョン。RC と GA を混ぜない（混ぜると Axon Server に接続できないことを IT1 スパイクで実測）。**版の上限は `axon-server-connector` の公開状況が決める**ので、上げるときはまず connector の公開版を確認する。Axon 5 系は minor でも API が動くため、版を上げるときは IT1 スパイクの #1・#2・#4 を再実行する |
| Cucumber 系の同期 | `cucumber-java` / `cucumber-junit-platform-engine` / `cucumber-spring` も同じバージョンで揃える。JUnit Platform 経由で動くので、JUnit 5 の版を上げるときは Cucumber の対応表を確認する |
| Axon Server との整合 | Axon Framework の版を上げるときは Axon Server の互換表を確認。Axon Server の版上げは計画停止 |
| 更新の頻度 | パッチは月次、マイナーは四半期、メジャーは ADR |
| 脆弱性 | CI の走査で高深刻度が出たら 7 日以内に更新 |
| 見直し | 本書は 90 日ごとに `stale` になる。IT のクローズ時に更新 |

## 参照

- [バックエンドアーキテクチャ](architecture_backend.md)、[フロントエンドアーキテクチャ](architecture_frontend.md)、[インフラストラクチャ](architecture_infrastructure.md)
- [ADR-0001](../../adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)、[ADR-0002](../../adr/cargo-tracker/0002-event-store-axon-server-and-postgresql-read-models.md)
- Axon Framework リリース: <https://discuss.axoniq.io/t/axon-and-axoniq-framework-release-5-3-0/6771>
- Axon Server 2026.0.4: <https://discuss.axoniq.io/t/axon-server-2026-0-4/6742>、Docker: <https://hub.docker.com/r/axoniq/axonserver>
- 参照元：`tmp/take-4/docs/design/tech_stack.md`、[java-3 技術スタック](../../article/source/java-3/docs/design/tech_stack.md)
