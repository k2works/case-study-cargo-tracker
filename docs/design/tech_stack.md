---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: design, tech-stack, java, spring-boot, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（Thymeleaf SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Java | 25 | アプリケーション実装言語 | 長期サポート、豊富なエコシステム、Spring Boot 4 との親和性 | Oracle Free Terms | GA（LTS。2025-09 リリース） |
| Spring Boot | 4.0.5 | アプリケーションフレームワーク | 自動構成による開発効率、Spring エコシステムの活用、DDD 実装との親和性 | Apache 2.0 | GA（4.0.5 リリース済み） |
| Spring Framework | 7.x | コアフレームワーク | Spring Boot 4 の基盤、JSpecify による null safety 強化、Jakarta EE 11 対応 | Apache 2.0 | GA（Spring Boot 4 に同梱） |
| Spring MVC | 7.x | Web フレームワーク | Thymeleaf Controller・REST Controller の統合、ヘキサゴナルの Primary Adapter として機能 | Apache 2.0 | GA（Spring Boot に同梱） |
| Spring Security | 7.x | 認証・認可 | フォームベース認証、RBAC（ROLE_SALES / ROLE_HANDLER 等）、CSRF 保護、セッション管理 | Apache 2.0 | GA（Spring Boot に同梱） |
| MyBatis | 4.0.1 | データアクセス | XML マッパーによる SQL の明示的管理、CQRS の Read Model クエリ最適化との親和性（mybatis-spring-boot-starter 4.0.1 で Spring Boot 4 対応済み） | Apache 2.0 | GA |
| springdoc-openapi | 3.0.2 | API ドキュメント（Swagger UI） | REST API の自動ドキュメント生成、`@ConditionalOnProperty` による環境別有効化 | Apache 2.0 | GA（Spring Boot 4 対応済み） |
| Spring Events | - | ドメインイベント発行 | `ApplicationEventPublisher` による CDI Events の代替、疎結合なコンテキスト間通信 | Apache 2.0 | GA（Spring Boot に同梱） |
| Thymeleaf Security | 3.x | テンプレートへの権限連携 | `sec:authorize` タグによるロール別 UI 制御（thymeleaf-extras-springsecurity6） | Apache 2.0 | GA（Spring Boot に同梱） |

> **バージョン採用方針**: Java 25 LTS + Spring Boot 4.0.x を採用する（ADR-001）。
> グリーンフィールドであるため、Java 21 + Spring Boot 3.4 で開始して後から移行する案は採らない。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Thymeleaf | 3.x | テンプレートエンジン（SSR） | Spring Boot との統合、サーバーサイドレンダリングによるシンプルな構成、SEO 対応 | Apache 2.0 | GA（安定版） |
| Bootstrap | 5.3.3 | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.4 | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |
| webjars-locator-lite | 1.0.1 | WebJars バージョン解決 | WebJars リソースパスのバージョン番号省略を実現 | MIT | GA |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番用 RDBMS・Repository テスト・E2E | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| H2 | 2.x | **ローカル開発時のアプリ起動のみ** | 起動が速く、画面を触るサイクルを短くできる（ADR-003）。PostgreSQL 互換モードで使用する | MPL 2.0 / EPL 1.0 | GA |
| Flyway | 10.x | DB マイグレーション | バージョン管理されたスキーマ変更、Spring Boot 統合、コンテキスト別マイグレーション管理 | Apache 2.0 | GA（Community Edition） |

> **DB の使い分け**（ADR-003）:
>
> | 用途 | DB |
> | :--- | :--- |
> | ローカルでのアプリ起動・画面確認 | H2（PostgreSQL 互換モード、インメモリ） |
> | **Repository / MyBatis Mapper のテスト** | **Testcontainers（実 PostgreSQL 16）** |
> | Controller 統合テスト・E2E | PostgreSQL |
> | 本番・ステージング | RDS PostgreSQL 16 |
>
> **SQL の正しさを H2 で判断しない。** 方言差（`TIMESTAMPTZ`・部分インデックス・`NUMERIC` の丸め）が本番障害として現れるため、
> SQL を検証する場所は実 PostgreSQL に固定する。H2 は `developmentOnly` 依存とし、本番の成果物に含めない。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| JUnit 5 | 5.x | テストフレームワーク | Java 標準のテストフレームワーク、パラメータ化テスト・入れ子テストクラス対応 | EPL 2.0 | GA（アクティブ開発中） |
| Mockito | 5.x | モックライブラリ | Spring Boot Test との統合、ドメインサービス・ポートのモック実装 | MIT | GA（アクティブ開発中） |
| AssertJ | 3.x | アサーションライブラリ | 流暢な API、集約・値オブジェクトのテストコードの可読性向上 | Apache 2.0 | GA（アクティブ開発中） |
| Testcontainers | 1.20.4 | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、Spring Boot 4 の `@ServiceConnection` 対応 | Apache 2.0 | GA |
| Spring MockMvc | - | Controller テスト | Spring MVC エンドポイントのテスト、Thymeleaf テンプレートのレンダリング検証 | Apache 2.0 | GA（Spring Boot に同梱） |
| ArchUnit | 1.4.1 | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（ドメイン層がインフラ層に依存しないこと等） | Apache 2.0 | GA（アクティブ開発中） |
| Playwright | 1.44+ | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |

> **ArchUnit の検証ルールは `test_strategy.md` §3.3 を正典とする。** 本表はツールの選定理由のみを記載し、ルールを再掲しない。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Gradle | 9.2.1 | ビルドツール（Groovy DSL） | 柔軟なビルド設定・依存関係管理・Spring Boot 4 プラグイン対応 | Apache 2.0 | GA |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| Checkstyle | - | Java コードスタイルチェック | コーディング規約の自動チェック | LGPL 2.1 | GA（アクティブ開発中） |
| SpotBugs | - | Java 静的解析 | バグパターンの自動検出、ドメインオブジェクトの null 安全性チェック | LGPL 2.1 | GA（アクティブ開発中） |

## 設計ドキュメント生成

コードと DB スキーマから設計情報を生成し、`docs/design/`（設計）との乖離を検出する。

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| JIG | 2026.7.4 | バイトコードからの設計ドキュメント生成 | ドメインモデル・パッケージ関連・業務機能一覧をコードから可視化し、設計書との乖離を差分として検出できる（`org.dddjava.jig-gradle-plugin`） | Apache 2.0 | GA（アクティブ開発中） |
| jig-erd | 0.2.2 | DB スキーマからの ER 図生成 | Flyway が構築した実スキーマから ER 図を出力し、`data-model.md` の ER 図との乖離を検出できる | Apache 2.0 | GA |
| Graphviz | 12.x 以降 | jig-erd の図描画 | jig-erd が SVG / PNG を出力する際に必要 | EPL 1.0 | GA |

> **生成物はコミットしない。** `build/jig/` と `build/jig-erd/` は Git 管理外とする。生成物をコミットすると「コードを変えたのに図が古い」状態がリポジトリに固定されるため、必要なときに生成する運用とする。
>
> **jig-erd は関連に着目した「ざっくりした」ER 図である。** PK・データ型・制約は扱わないため、それらの正典は `data-model.md` のままである。jig-erd で確認するのはテーブルと外部キーの関係が設計どおりかであり、カラム定義の詳細ではない。

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | 環境の再現性、開発・本番環境の一貫性、マルチステージビルドによる本番イメージ最小化 | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（アプリ + PostgreSQL + SonarQube）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・API キーの安全な管理、Spring Boot 統合 | - | GA（AWS マネージド） |
| AWS CloudWatch | - | 監視・ログ | アプリケーションログ・メトリクス・アラートの統合管理 | - | GA（AWS マネージド） |
| AWS Route 53 | - | DNS | ドメイン管理、ヘルスチェックフェイルオーバー | - | GA（AWS マネージド） |
| AWS ACM | - | TLS 証明書 | HTTPS 証明書の自動更新 | - | GA（AWS マネージド） |

## ドキュメント

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs | 1.x | ドキュメントサイト生成 | Markdown ベース、Material テーマ、PlantUML 統合 | BSD 2-Clause | GA（アクティブ開発中） |
| PlantUML | - | ダイアグラム生成 | UML 図・ER 図・ワイヤーフレームのコードベース管理、テキストから図を生成 | GPL 3.0 | GA（アクティブ開発中） |
| Mermaid | 10.x | ダイアグラム生成 | Markdown 内インライン図表、MkDocs 統合 | MIT | GA（アクティブ開発中） |

## 開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| IntelliJ IDEA | - | IDE | Java / Spring Boot 開発の標準 IDE、DDD パターン対応リファクタリング支援 | Commercial / Community | GA（JetBrains サポート） |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

**本システムは外部システムと HTTP 連携しない**（ADR-006）。経路算出・通関・決済・港湾・通知はいずれも内部シミュレーションで代替する。

そのため、外部 ACL ポート（`ExternalRoutingServicePort` 等）、Spring WebClient による外部 API クライアント、WireMock による契約テストはいずれも採用しない。将来、実際の連携先が定まった時点で ADR-006 を改訂し、`test_strategy.md` に記載の復帰手順に従って導入する。

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Java: LTS バージョンのみを採用する。現行は Java 25 LTS（ADR-001）
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- Spring Boot: 4.x のマイナーバージョンは積極的に追従する（4.0 → 4.1 → 4.2）

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Java | 25 | 29（次 LTS） | 2027 年 | JVM 設定、ライブラリ互換性 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Spring Boot | 4.0.x | 4.x 最新 | 随時 | 自動構成の変更確認 |
| Flyway | 10.x | 11.x | メジャー変更時 | マイグレーションスクリプト |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Spring Boot エコシステムで自然に実現できる技術を優先した。
   特に MyBatis による SQL の明示的管理は CQRS の Read Model 最適化に適合する。

2. **外部依存の排除**: 外部システム連携を実装対象から外し、内部シミュレーションで代替した（ADR-006）。
   実体のない連携先に対する契約テストは契約を保証しないため、YAGNI に従い必要になった時点で導入する。

3. **テスト容易性**: ArchUnit によるアーキテクチャルールの自動検証を追加し、ヘキサゴナルアーキテクチャの依存関係制約が
   コードベースに継続的に適用されることを保証する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。

5. **設計と実装の乖離検出**: JIG と jig-erd により、コードと DB スキーマから設計情報を生成する。
   設計書の図を手で更新し続ける運用は必ず破綻するため、**乖離を人間の注意力ではなく生成物の差分で検出する**。
