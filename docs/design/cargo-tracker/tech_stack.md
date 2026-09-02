---
type: Design
title: "技術スタック - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）"
description: "CQRS / Event Sourcing 版 Cargo Tracker の技術スタック一覧（調査時点 2026-09-02）。Java 25 / Spring Boot 4.1 / Axon Framework 5.3 / Axon Server 2026.0.4 / MyBatis / PostgreSQL 16 / React 19 と、IT1 スパイクで確認する事項、採用しないもの、バージョン管理方針。"
tags: [design,tech-stack,axon,spring-boot,react]
status: stable
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T07:46:35Z }
stale_after: 2026-12-01T00:00:00Z
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
---

# 技術スタック - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）

## 概要

[バックエンド](architecture_backend.md)・[フロントエンド](architecture_frontend.md)・[インフラ](architecture_infrastructure.md)の各アーキテクチャに基づく技術スタックの一覧です。バージョンは **調査時点（2026-09-02）** の値であり、実装着手時に再確認します。本書には 90 日の見直し期限を付けます。

**本書が正典です。** 他の設計文書に書かれたバージョンと食い違う場合は本書に従います。

| 参照元 | 採るもの | 変えるもの |
| :--- | :--- | :--- |
| `tmp/take-4/docs/design/tech_stack.md` | Java 25 / Spring Boot 4 / Axon 5 / Axon Server 2026 / MyBatis / PostgreSQL 16 / Gradle 9 の組み合わせ、バージョン実在性のチェックリスト、LTS 追随ルール | H2・RestTemplate・Spring Cloud Contract を外す。Axon 系を 5.3 に上げ、Jackson 3 を前提にする |
| `docs/article/source/java-3/docs/design/tech_stack.md` | フロントエンド（React 19 / Vite / TanStack Query / Zustand / Tailwind）、kind + Kustomize、ECS + RDS | RabbitMQ / Spring Cloud Stream / CloudAMQP / Amazon MQ を外す |

## 実装着手前の確認（IT1 スパイク）

バージョンは Maven Central と Docker Hub で実在を確認してから `libs.versions.toml` に固定します。次は本書だけでは確定できず、スパイクで確かめる項目です（[ADR-0001](../../adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md) の決定 5）。結果はスパイク終了時に ADR-0001 と `architecture_backend.md` に書き戻します。

| # | 確認事項 | 確認方法 |
| :--- | :--- | :--- |
| 1 | **集約の登録 API**：Axon 5.3 系で `@EventSourcedEntity` 単独（Spring stereotype 無し）で集約が Command Bus に登録されるか。登録されなければ標準は `@EventSourced(idType, tagKey)`（`org.axonframework.extension.spring.stereotype`。take-4 ADR-0008 の最終決定）とし、ArchUnit の許可リストに加える | bootJar を起動して `POST` 1 本（`@MockitoBean CommandGateway` を使わない） |
| 2 | `axon-spring-boot-starter` 5.3 系 GA が Maven Central にあり、`axon-server-connector` と `axon-test` が **同じバージョン**で揃うこと（RC と GA の ABI が食い違う。take-4 ADR-0009） | `./gradlew dependencies` |
| 3 | Spring Boot 4.1（Jackson 3 既定）で Axon の JDBC 自動設定（Token / Saga Store）が働くこと。働かなければ手動構成。Axon の `TransactionManager` Bean が **1 つ**であること、`SpringTransactionManager` を第 3 引数 `ConnectionProvider` 付きで作ること、`token_entry` に `mask INTEGER NOT NULL` があること | 起動と統合テスト（投影の SQL を故意に失敗させトークンが進まないこと） |
| 4 | Axon Server の context が **DCB** で作られていること（`AXONIQ_AXONSERVER_STANDALONE_DCB=true`）と、起動時の接続検査が DCB 無効の context を赤にすること | Testcontainers で DCB 無効の Axon Server を立て、起動が止まること 1 本 |
| 5 | `AxonTestFixture.with(...)` の組み立て方 | 集約テスト 1 本 |
| 6 | Saga のアノテーション名（`@Saga` / `@SagaEventHandler` / `SagaLifecycle` の 5 系での名称） | Saga テスト 1 本 |
| 7 | Axon Server 2026.0.x と Axon Framework 5.3 の組み合わせで、サービス越しの Command / Query が届くこと | 往復テスト 1 本 |
| 8 | `PostgresqlEventStorageEngine` の公開状況（[ADR-0002](../../adr/cargo-tracker/0002-event-store-axon-server-and-postgresql-read-models.md) の再評価条件） | Maven Central |
| 9 | S3 へエクスポートした Event Store からの**差分再投入**が可能か（RPO の根拠。参照元で未検証） | エクスポート → 追記 → 差分投入 → イベント数一致 |

## バックエンド

### 言語・ランタイム

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| Java | 25（LTS） | 開発言語 | 2033-09 | Axon 5 / Spring Boot 4 の要求（17 以上）を満たす最新 LTS。`record`・パターンマッチ・Virtual Threads |
| Eclipse Temurin | 25 | JVM・コンテナイメージ | 2033-09 | `eclipse-temurin:25-jre-alpine` |

### フレームワーク

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| Spring Boot | 4.1 系 | サービスの基盤 | OSS サポート 4.1 系は 2027 年前半（確認要） | Spring Framework 7、Jackson 3 既定。参照元と同系 |
| **Axon Framework** | **5.3 系** | CQRS / Event Sourcing / Saga / 分散バス | コミュニティ | [ADR-0001](../../adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md)。5 系の Entity API（`@EntityCreator` / `EventAppender` / `@EventSourcingHandler`）。集約の登録は `@EventSourced(idType, tagKey)`（Spring stereotype、上記 #1） |
| `axon-spring-boot-starter` | Axon と同じ | Spring Boot 統合 | — | `org.axonframework.extensions.spring`。`@EventSourced` はこの starter が提供する（`axon-spring`） |
| `axon-server-connector` | Axon と同じ | Axon Server への gRPC 接続 | — | **明示依存**。starter の推移的依存に含まれない。起動時に接続と **DCB context** を検査する |
| **Axon Server** | **2026.0.4**（Standard Edition） | Event Store / Command Bus / Event Bus / Query Bus | AxonIQ | [ADR-0002](../../adr/cargo-tracker/0002-event-store-axon-server-and-postgresql-read-models.md)。Docker `axoniq/axonserver:2026.0.4`（SE / EE は同一イメージ、ライセンスの有無で切り替わる）。`AXONIQ_AXONSERVER_STANDALONE_DCB=true` が必須（クラスタは `dcb=true`） |
| Spring Cloud Gateway | Spring Boot 4.1 対応版 | ルーティング・JWT 検証・CORS・レート制限 | — | `gatewayms` |
| Spring Security | 7 系 | 認証・認可 | — | Spring Boot 4 と整合 |
| jjwt | 0.12 系 | JWT 発行・検証 | — | `authms` と `shared` |
| Jackson | 3 系（Spring Boot 4 同梱） | イベントのシリアライズ | — | Axon のシリアライザ。IT1 で Axon との整合を確認 |
| Micrometer Tracing | Spring Boot 同梱 | サービスをまたぐ相関 | — | イベントのメタデータに `traceId` |
| Resilience4j | 2 系 | Saga の再試行・Query Bus 呼び出しのタイムアウト（既定 5 秒） | — | Saga / Reaction Handler から同期クエリを呼ばない |

### データアクセス（投影・Auth）

| 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16 系 | 投影 DB × 5、`auth_db` | 2028-11 | 参照元と同じ。RDS で Multi-AZ |
| MyBatis | 3.5 系 | 投影の書き込み・Query Handler の SQL | — | SQL を自分で持つ。JPA は採らない |
| `mybatis-spring-boot-starter` | 3 系（Spring Boot 4 対応版） | Spring 統合 | — | |
| Flyway | 11 系 | サービスごとのマイグレーション | — | `V<num>__<desc>.sql`。適用済みは編集しない |
| HikariCP | Spring Boot 同梱 | 接続プール | — | |
| Axon `JdbcTokenStore` / `JdbcSagaStore` | Axon 同梱 | Token / Saga の永続化 | — | 投影と同一 DataSource・同一トランザクション。`TransactionManager` Bean は 1 つ、`token_entry.mask INTEGER NOT NULL` |
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
| `axon-test` | Axon と同じ | `AxonTestFixture`（集約・Saga） |
| Testcontainers | 1.20 以上 | PostgreSQL・Axon Server の起動 |
| Awaitility | 4 系 | 投影の反映待ち |
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
| Axon 系の同期 | `axon-spring-boot-starter` / `axon-server-connector` / `axon-test` は必ず同じバージョン。RC と GA を混ぜない。Axon 5 系は minor でも API が動く（`@EventSourcedEntity` → `@EventSourced` の経緯）ため、版を上げるときは IT1 の確認事項 #1・#3・#4 を再実行する |
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
