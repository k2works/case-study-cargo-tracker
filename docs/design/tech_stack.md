---
title: 技術スタック - 国際貨物輸送管理システム
description: バックエンド・フロントエンド・インフラ・運用すべての採用技術一覧。バージョン・サポート期限・選定理由・アップグレード方針を含む。
published: true
date: 2026-05-12T00:00:00.000Z
tags: design, tech-stack, versions, lts, axon-5, spring-boot, react
---

# 技術スタック - 国際貨物輸送管理システム

## 概要

本プロジェクトで採用するすべての技術を一覧化し、バージョン・サポート期限・選定理由を明示する。技術選定の指針は次のとおり。

- **LTS（Long Term Support）優先**: Java・Node.js・PostgreSQL 等の基盤技術は LTS バージョンを採用する
- **アーキテクチャ整合**: Axon Framework 5 + CQRS + Event Sourcing 構成に整合する技術を選定する
- **エコシステム成熟度**: コミュニティ活性度・ドキュメント充実度を評価する
- **アップグレード性**: メジャーバージョン更新の影響を予測し、計画的に追随する
- **コスト**: OSS / クラウドマネージドサービスのバランスを取る

サポート期限（EOL）の最新情報は各プロジェクトの公式サイトで確認すること。本ドキュメントは作成時点（2026-05）の情報。

## 実装着手前の確認チェックリスト（バージョン実在性）

主要技術のバージョン採用前に **公式情報源で GA 時期と EOL を確認** する。チェックは IT0（着手準備イテレーション）で SRE リードが実施する。

| 技術 | 採用バージョン | 公式情報源 | 確認すべき項目 |
| :--- | :--- | :--- | :--- |
| Java（OpenJDK） | 25 LTS | <https://openjdk.org/projects/jdk/25/>, <https://endoflife.date/java> | 25 が LTS であること、Premier Support 期限、Eclipse Temurin の対応有無 |
| Eclipse Temurin | 25 LTS | <https://adoptium.net/temurin/releases/> | `eclipse-temurin:25-jre-alpine` イメージの GA、Long Term Support 表記 |
| Spring Boot | 4.0.x | <https://spring.io/projects/spring-boot>, <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Versions> | 4.0 GA 日付、Spring Framework 7 対応、OSS サポート期限 |
| Spring Framework | 7.x | <https://spring.io/projects/spring-framework> | 7.x GA 日付、Spring Boot 4 との互換 |
| Spring Security | 7.x | <https://spring.io/projects/spring-security> | 7.x GA、Spring Boot 4 同梱 |
| Spring Cloud Gateway | 4.x | <https://spring.io/projects/spring-cloud-gateway> | Spring Boot 4 対応版 |
| Axon Framework | 5.x | <https://www.axoniq.io/products/axon-framework>, <https://github.com/AxonFramework/AxonFramework/releases> | 5.x GA、Spring Boot 4 公式サポート、Maven Central 公開状況 |
| Axon Server SE | 2026.0.0 | <https://www.axoniq.io/products/axon-server>, <https://docs.axoniq.io/axon-server-reference/>, <https://hub.docker.com/r/axoniq/axonserver/tags> | Docker タグの実在確認（`axoniq/axonserver:2026.0.0`、`-LTS` サフィックスは現リリースモデルには存在せず連番タグのみ）、Axon Framework 5.1 クライアント互換 |
| Hibernate ORM（参考のみ、未採用） | - | - | MyBatis 採用のため不要（ADR-0002） |
| MyBatis | 3.5.x | <https://mybatis.org/mybatis-3/>, <https://github.com/mybatis/mybatis-3/releases> | 3.5.x 最新パッチ、JDK 25 対応 |
| mybatis-spring-boot-starter | 3.0.x | <https://github.com/mybatis/spring-boot-starter/releases> | Spring Boot 4 対応版 |
| PostgreSQL | 16.x | <https://www.postgresql.org/support/versioning/>, <https://endoflife.date/postgresql> | EOL 2028-11、AWS RDS 対応 |
| Flyway | 10.x | <https://documentation.red-gate.com/fd/release-notes-and-older-versions-179732572.html> | 10.x 最新、PostgreSQL 16 対応 |
| Gradle | 9.2.x | <https://docs.gradle.org/current/release-notes.html>, <https://endoflife.date/gradle> | 9.2.x GA、JDK 25 対応 |
| Node.js | 22 LTS | <https://nodejs.org/en/about/previous-releases>, <https://endoflife.date/nodejs> | LTS 名称（Jod 等）、EOL 2027-04 |
| React | 19.x | <https://react.dev/blog>, <https://github.com/facebook/react/releases> | 19.x GA、Concurrent Mode 安定 |
| Vite | 6.x | <https://vitejs.dev/guide/migration.html>, <https://github.com/vitejs/vite/releases> | 6.x GA、React 19 対応 |
| TypeScript | 5.6+ | <https://www.typescriptlang.org/docs/handbook/release-notes/overview.html> | 5.6+ Stable |
| TanStack Query | 5.x | <https://tanstack.com/query/latest> | 5.x Stable、React 19 対応 |
| Tailwind CSS | 4.x | <https://tailwindcss.com/blog>, <https://github.com/tailwindlabs/tailwindcss/releases> | 4.x GA、Vite 6 / PostCSS 連携 |
| Testcontainers (Java) | 1.20.x | <https://www.testcontainers.org/>, <https://github.com/testcontainers/testcontainers-java/releases> | JDK 25 対応、PostgreSQL 16 イメージ |
| Playwright | 1.50+ | <https://playwright.dev/docs/release-notes> | Chrome / Firefox / WebKit 最新版 |
| Terraform | 1.10+ | <https://github.com/hashicorp/terraform/releases>, <https://www.hashicorp.com/blog/terraform-license-update>（BSL 注意） | 1.10+ Stable、AWS Provider 5.x |

> **重要**: 本表のバージョン番号と日付は分析時点（2026-05）の **想定値** であり、実装着手前に **必ず公式情報源で検証** すること。検証結果に基づき本表と関連 ADR を更新する。
>
> **代替案の準備**: 上記いずれかの技術が GA 未達 or 互換性問題で採用不可となった場合の代替案：
>
> | 採用技術 | 代替案 |
> | :--- | :--- |
> | Spring Boot 4.0 | Spring Boot 3.3 LTS（Java 17・Spring Framework 6 系列） |
> | Java 25 LTS | Java 21 LTS（2031-09 EOL） |
> | Axon Framework 5 | Axon Framework 4.10.x（Spring Boot 3.3 と組合せ） |
> | Axon Server 2026.0.0 | Axon Server 2024.2.22（過去 LTS ラインの最新パッチ、保守的選択） |
> | React 19 | React 18.3（LTS 風サポート） |
> | Tailwind CSS 4 | Tailwind CSS 3.4（成熟版） |

## バックエンド技術スタック

### 言語・ランタイム

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 言語 | Java | **25** | バックエンド開発言語 | 2033-09（LTS） | Axon 5 / Spring Boot 4 が要求する Java 17 以上を満たす最新 LTS。Pattern matching・Records・Virtual Threads 等の最新言語機能を活用 |
| JVM | Eclipse Temurin | 25 LTS | JVM 実装 | 2033-09 | OSS で広く使われる Temurin を採用。Docker イメージ `eclipse-temurin:25-jre-alpine` で配布 |

### フレームワーク・アプリケーション基盤

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| アプリケーションフレームワーク | Spring Boot | **4.0.x** | マイクロサービスの基盤 | 2027-12（コミュニティ） | Jakarta EE 11 対応、Java 17+ 必須、Axon 5 と整合 |
| CQRS / Event Sourcing / Saga | **Axon Framework** | **5.x** | コマンド・イベント・クエリ・Saga | コミュニティ | ADR-0001 に基づく中核選択。アノテーション + 機能ベース API |
| メッセージング基盤 | **Axon Server** | **2026.0.0** (Standard Edition) | Event Store / Command Bus / Event Bus / Query Bus | コミュニティ | Axon Framework 5.1 同時期リリース、単一ノードで運用。Docker タグは `axoniq/axonserver:2026.0.0`（連番リリースモデル） |
| API ゲートウェイ | Spring Cloud Gateway | 4.x | リバースプロキシ・JWT 検証 | 2027-12 | Spring Boot 4 系列と整合 |
| セキュリティ | Spring Security | 7.x | 認証・認可・JWT 検証 | コミュニティ | Spring Boot 4 と整合 |
| JWT ライブラリ | jjwt | 0.12.x | JWT 発行・検証 | コミュニティ | Spring Security との組合せで標準的 |

### データアクセス・永続化（Read Model 側）

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| SQL マッパー | **MyBatis** | 3.5.x | Read Model / Auth DB の永続化（Projection 更新・Query） | コミュニティ | SQL の見える化・チューニング容易性、参考プロジェクト（Practical DDD in Enterprise Java）との整合 |
| Spring 統合 | **mybatis-spring-boot-starter** | 3.0.x | Spring Boot との統合（DataSource・トランザクション・Mapper スキャン） | コミュニティ | 標準的な統合方法 |
| Axon 永続化 | `JdbcTokenStore` / `JdbcSagaStore` | Axon 5 同梱 | Event Processor の Token・Saga インスタンスの永続化 | コミュニティ | MyBatis と同一 DataSource を共有し、Projection 更新と同一トランザクションで処理 |
| データベース | **PostgreSQL** | **16.x** | Read Model / Auth DB | **2028-11** | Read Model の関係型ストア。インデックス・JSON 型・FTS 等の機能が豊富 |
| 組込み DB（開発・テスト） | H2 Database | 2.x | 開発・統合テスト用 | コミュニティ | Testcontainers が利用できない局面の代替 |
| マイグレーション | Flyway | 10.x | Read Model / Auth DB スキーマ管理 | コミュニティ | `V<num>__<desc>.sql` 命名規則で運用 |
| 接続プール | HikariCP | （Spring Boot 同梱） | コネクションプール | コミュニティ | Spring Boot 既定 |

### API・通信

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 同期通信 | RestTemplate / WebClient | （Spring 同梱） | サービス間 REST 同期通信 | 2027-12 | ACL（`ExternalCargoRoutingService` 等）で使用 |
| API ドキュメント | springdoc-openapi | 3.0.3 | OpenAPI 3 仕様書自動生成・Swagger UI 提供 | コミュニティ | Spring Boot 4 対応版（3.x 系）。`/swagger-ui.html` と `/v3/api-docs` を自動公開。フロントエンドの型生成にも使用 |
| JSON ライブラリ | Jackson | 2.18.x | シリアライズ・デシリアライズ | コミュニティ | Axon の既定シリアライザ |

### ビルド・依存管理

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ビルドツール | **Gradle** | **9.2.x** | ビルド・依存管理 | コミュニティ | マルチプロジェクト構成・ビルドキャッシュが高速 |
| Gradle Wrapper | gradle-wrapper | 9.2.1 | バージョン固定 | - | リポジトリに同梱、開発環境を統一 |

### テスト（バックエンド）

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ユニットテスト | JUnit 5 | 5.11+ | テストフレームワーク | コミュニティ | デファクトスタンダード |
| モック | Mockito | 5.x | モックライブラリ | コミュニティ | JUnit 5 と統合済み |
| アサーション | AssertJ | 3.26+ | 流暢アサーション | コミュニティ | 可読性の高いアサーション |
| Axon テスト | **Axon Test** | **5.x** | `AggregateTestFixture` / `SagaTestFixture` | コミュニティ | Given-When-Then で集約・Saga を検証 |
| 統合テスト | Testcontainers | 1.20.x | PostgreSQL / Axon Server のコンテナ起動 | コミュニティ | 実 DB・実 Axon Server で結合検証 |
| Contract テスト | Spring Cloud Contract | 4.x | イベント契約・REST API 契約 | コミュニティ | クロスサービスのスキーマ検証 |
| アーキテクチャテスト | ArchUnit | 1.4+ | パッケージ依存・アノテーション利用ルール検証 | コミュニティ | ADR-0001 のコンプライアンス自動化 |

### 品質管理（バックエンド）

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 静的解析 | Checkstyle | 10.21.x | コーディング規約 | コミュニティ | カスタムルール対応 |
| バグ検出 | SpotBugs | 6.1.x | 潜在バグ検出 | コミュニティ | FindBugs 後継 |
| カバレッジ | JaCoCo | 0.8.x | テストカバレッジ計測 | コミュニティ | Gradle プラグイン充実 |
| 品質ダッシュボード | SonarQube | 10.x（Community Edition） | コード品質可視化 | コミュニティ | 品質ゲート定義に使用 |
| Sonar スキャナ | sonar-gradle-plugin | 6.x | Gradle 連携 | コミュニティ | Sonar への自動連携 |

## フロントエンド技術スタック

### フレームワーク・言語

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| フレームワーク | **React** | **19.x** | SPA 構築 | コミュニティ | エコシステム最大、Server Components 等の最新機能 |
| 言語 | **TypeScript** | **5.6+** | 型付き JavaScript | コミュニティ | 型安全性、IDE 補完、API DTO の型保証 |
| ランタイム | Node.js | **22 LTS** | ビルド・テスト | **2027-04** | Active LTS、Vite/Vitest との互換性 |
| パッケージマネージャ | npm | 10+（Node 22 同梱） | 依存管理 | - | 標準同梱、CI と一貫 |

### ビルド・開発ツール

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ビルドツール | **Vite** | **6.x** | 開発サーバ・本番ビルド | コミュニティ | ESBuild + Rollup の高速ビルド |
| トランスパイラ | SWC | （Vite 同梱） | 高速変換 | コミュニティ | Vite の既定 |
| Linter | ESLint | 9.x | 静的解析 | コミュニティ | Flat Config |
| Formatter | Prettier | 3.x | コードフォーマッタ | コミュニティ | デファクト |

### 状態管理・データ取得

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| サーバー状態管理 | **TanStack Query (React Query)** | **5.x** | Read Model のキャッシュ・再フェッチ | コミュニティ | CQRS の Query 側と対応。`invalidateQueries` で結果整合性吸収 |
| クライアント状態管理 | **Zustand** | **5.x** | 認証情報・UI 状態 | コミュニティ | 軽量・低ボイラープレート |
| HTTP クライアント | fetch (built-in) | - | API 呼出 | - | 標準 API、追加依存なし |
| API 型生成 | openapi-typescript | 7.x | OpenAPI → TypeScript 型 | コミュニティ | バックエンドの springdoc 出力を型化 |

### UI / スタイリング

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| スタイリング | **Tailwind CSS** | **4.x** | ユーティリティ CSS | コミュニティ | 開発速度、デザインシステムとの整合 |
| アイコン | Lucide React | 最新 | アイコンセット | コミュニティ | 軽量・整合性の高いアイコン |
| ルーティング | React Router | 7.x | ルーティング | コミュニティ | デファクト |
| フォーム | React Hook Form | 7.x | フォーム状態管理 | コミュニティ | 高性能・バリデーション統合 |
| バリデーション | Zod | 3.x | スキーマバリデーション | コミュニティ | TypeScript 親和性 |
| トースト | sonner | 1.x | 通知 UI | コミュニティ | Tailwind 親和性 |
| 日付ライブラリ | date-fns | 4.x | 日付操作・フォーマット | コミュニティ | Tree shakable |

### フロントエンドテスト

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ユニットテスト | **Vitest** | 2.x | フックやユーティリティのテスト | コミュニティ | Vite と統合、高速 |
| コンポーネントテスト | Testing Library (react) | 16.x | コンポーネント・統合テスト | コミュニティ | アクセシビリティ志向 |
| モックサーバー | MSW (Mock Service Worker) | 2.x | API モック | コミュニティ | Contract テスト・統合テスト |
| E2E テスト | **Playwright** | 1.50+ | エンドツーエンドテスト | コミュニティ | クロスブラウザ・CI 親和性 |

## インフラ技術スタック

### コンテナ・オーケストレーション

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| コンテナランタイム | Docker | 27.x | コンテナ実行 | コミュニティ | デファクト |
| ローカルオーケストレーション | Docker Compose | v2.30+ | 開発環境のサービス起動 | コミュニティ | Single-host で複数サービス |
| 本番オーケストレーション | **AWS ECS** | - | アプリ層は Fargate、Axon Server は EC2 起動タイプ | - | サーバレス + ステートフル併用が可能 |
| コンテナレジストリ | **GitHub Container Registry (GHCR)** | - | Docker イメージ管理（`ghcr.io/<owner>/...`） | コミュニティ | リポジトリと統合、CI で `GITHUB_TOKEN` を直接利用可（ADR-0003） |

### クラウドサービス（AWS）

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| クラウドプロバイダ | **AWS** | - | 本番・ステージング | - | 国内事例豊富、コスト透明性 |
| 計算 | ECS Fargate | - | アプリ層コンテナ | - | サーバレス、運用負荷低 |
| 計算（ステートフル） | ECS on EC2 | - | Axon Server | - | EBS マウントが必要 |
| ロードバランサ | ALB | - | アプリ層 LB / TLS 終端 | - | HTTP/HTTPS / Health Check |
| データベース | Amazon RDS (PostgreSQL) | 16.x | 6 つの Read Model + Auth DB | 2028-11 | マネージドで運用負荷低 |
| ストレージ（Event Store） | EBS gp3 | - | Axon Server の Event Store | - | 高 IOPS、スナップショット可能 |
| ストレージ（バックアップ） | S3 | - | ログ・Event Store エクスポート | - | 監査ログ 7 年保持 |
| 証明書 | AWS Certificate Manager (ACM) | - | TLS 証明書 | - | 無料・自動更新 |
| シークレット管理 | AWS Secrets Manager | - | DB 認証情報・JWT 鍵 | - | ローテーション対応 |
| 設定管理 | AWS Systems Manager Parameter Store | - | 環境設定 | - | 低コスト |
| バックアップ | AWS Backup | - | EBS スナップショット管理 | - | クロスサービスバックアップ |
| 監視 | Amazon CloudWatch | - | メトリクス・ログ・アラーム | - | AWS 統合 |
| Web 配信 | CloudFront（必要時） | - | フロントエンド配信加速 | - | CDN・キャッシュ |

### IaC・CI/CD

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| IaC | **Terraform** | 1.10+ | AWS インフラ定義 | コミュニティ | マルチクラウド可、エコシステム最大 |
| CI/CD | **GitHub Actions** | - | ビルド・テスト・デプロイ自動化 | - | リポジトリ統合、ランナー柔軟 |
| デプロイ補助 | AWS CDK（任意） | 2.x | TS でのインフラ補助 | - | アプリケーション固有のリソース定義 |
| イメージ Publish 認証 | `GITHUB_TOKEN`（GHCR push） | - | CI から GHCR への push | - | Phase 0 採用、追加シークレット不要（ADR-0003） |
| AWS デプロイ認証 | OIDC（GitHub → AWS） | - | キーレスデプロイ（ECS/RDS 操作） | - | Phase 1 で設定、アクセスキー不要 |
| シークレット管理（CI） | GitHub Actions Secrets / OIDC | - | CI/CD のシークレット | - | リポジトリ単位の安全管理 |

### 監視・ロギング・トレーシング

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 選定理由 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| メトリクス | CloudWatch Metrics | - | インフラ・アプリメトリクス | - | AWS 統合 |
| ログ | CloudWatch Logs | - | アプリケーションログ集約 | - | ログ保持・検索 |
| アラート | CloudWatch Alarms + SNS | - | 閾値超過時の通知 | - | メール・Slack 通知 |
| メトリクス公開 | Micrometer | 1.13+ | Spring Boot のメトリクス出力 | コミュニティ | CloudWatch / Prometheus 両対応 |
| 分散トレーシング | OpenTelemetry | 1.x | サービス間トレース | コミュニティ | Axon Framework と統合 |
| エラートラッキング（FE） | Sentry | 8.x | ブラウザ例外・パフォーマンス | 商用無料枠 | フロントエンドの可視化 |
| Axon 監視 | Axon Server Console | 2024.x 同梱 | コマンド・イベント・Saga 状態 | - | Axon 標準 |

## データベース・メッセージング（再掲）

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 |
| :--- | :--- | :--- | :--- | :--- |
| メッセージング | Axon Server (Standard Edition) | 2026.0.0 | Command/Event/Query Bus + Event Store | コミュニティ |
| RDBMS | PostgreSQL | 16.x | Read Model / Auth DB | 2028-11 |
| 接続ドライバ | PostgreSQL JDBC Driver | 42.7+ | Java からのアクセス | コミュニティ |

## バージョン管理方針

### LTS / メジャーバージョンの追随ルール

| 技術 | 追随ポリシー |
| :--- | :--- |
| Java | LTS のみ採用。次回 LTS（Java 29、2027 年予定）リリース後、半年以内に評価・1 年以内に移行 |
| Spring Boot | メジャーバージョンは GA から半年経過後にアップグレード検討 |
| Axon Framework | マイナーは即追随、メジャーは EE 移行と合わせて計画 |
| PostgreSQL | メジャーは EOL 1 年前までに上げる |
| Node.js | Active LTS のみ採用。Maintenance LTS に降格したら次の LTS へ移行 |
| React | マイナーは即追随。メジャーは半年経過後 |
| Tailwind CSS | メジャー追随、Breaking Changes は CHANGELOG で評価 |

### サポート期限のサマリ

| 技術 | EOL / サポート期限 | 期限到達時の対応 |
| :--- | :--- | :--- |
| Java 25 | 2033-09 | Java 29 LTS への移行を計画 |
| Spring Boot 4.x | 2027-12 頃 | 5.x へアップグレード |
| Axon Framework 5.x | コミュニティサポート | 6.x（または EE）への移行検討 |
| PostgreSQL 16 | 2028-11 | 17 または 18 へアップグレード |
| Node.js 22 | 2027-04 | 次の LTS（24）へ移行 |

### アップグレード計画

1. **検知**: Dependabot / Renovate で依存関係の更新通知を週次自動受信
2. **評価**: メジャーバージョン更新は ADR で評価して採用判断
3. **試験**: feature ブランチで影響範囲を測定（テスト・ArchUnit・E2E）
4. **段階適用**: 開発 → ステージング → 本番（Blue/Green）の順
5. **モニタリング**: 適用後 1 週間はメトリクスを重点監視

## 依存関係の管理ツール

| 用途 | ツール | 設定ファイル |
| :--- | :--- | :--- |
| バックエンド依存定義 | Gradle | `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`（Version Catalog） |
| フロントエンド依存定義 | npm | `package.json`, `package-lock.json` |
| 脆弱性スキャン（BE） | OWASP Dependency Check / Snyk | CI で実行 |
| 脆弱性スキャン（FE） | npm audit / Snyk | CI で実行 |
| 依存更新自動化 | Dependabot | `.github/dependabot.yml` |

## ライセンス確認方針

| 区分 | 許容ライセンス | NG ライセンス |
| :--- | :--- | :--- |
| バックエンド | Apache 2.0、MIT、BSD、EPL 2.0、LGPL 2.1+ | AGPL、SSPL、Commons Clause |
| フロントエンド | MIT、Apache 2.0、ISC、BSD | AGPL、Commons Clause |
| 商用ライセンス | 必要に応じて評価 | - |

Axon Server Standard Edition のライセンス（AxonIQ Open Source License）は **OSS だが商用利用は EE 提案対象** であるため、商用フェーズ前に EE 移行可否を再評価する。

## 環境別バージョン差異

| 技術 | 開発（ローカル） | 開発環境（AWS） | ステージング | 本番 |
| :--- | :--- | :--- | :--- | :--- |
| Java JRE | Temurin 25 | Temurin 25 | Temurin 25 | Temurin 25 |
| Node | 22 LTS | 22 LTS | 22 LTS | 22 LTS |
| PostgreSQL | 16（Docker） | RDS 16 | RDS 16 | RDS 16 |
| Axon Server | 2026.0.0（Docker） | ECS EC2 2026.0.0 | ECS EC2 2026.0.0 | ECS EC2 2026.0.0 |
| Spring Boot | 4.0.x | 4.0.x | 4.0.x | 4.0.x |

すべての環境でメジャー・マイナーバージョンを揃え、Docker イメージタグで管理する。

## 参照

- [バックエンドアーキテクチャ](architecture_backend.md)
- [フロントエンドアーキテクチャ](architecture_frontend.md)
- [インフラストラクチャアーキテクチャ](architecture_infrastructure.md)
- [ドメインモデル設計](domain-model.md)
- [データモデル設計](data-model.md)
- [UI 設計](ui_design.md)
- [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](../adr/0001-axon-framework-adoption.md)
