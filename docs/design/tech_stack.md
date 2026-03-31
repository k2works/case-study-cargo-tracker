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
| Java | 25 | アプリケーション実装言語 | 長期サポート、豊富なエコシステム、Spring Boot 4 との親和性 | Oracle Free Terms | GA（LTS: Java 21、Java 25 は LTS 候補） |
| Spring Boot | 4.0.x | アプリケーションフレームワーク | 自動構成による開発効率、Spring エコシステムの活用、DDD 実装との親和性 | Apache 2.0 | GA（4.0.5 リリース済み） |
| Spring Framework | 7.x | コアフレームワーク | Spring Boot 4 の基盤、JSpecify による null safety 強化、Jakarta EE 11 対応 | Apache 2.0 | GA（Spring Boot 4 に同梱） |
| Spring MVC | 7.x | Web フレームワーク | Thymeleaf Controller・REST Controller の統合、ヘキサゴナルの Primary Adapter として機能 | Apache 2.0 | GA（Spring Boot に同梱） |
| Spring Security | 7.x | 認証・認可 | フォームベース認証、RBAC（ROLE_SALES / ROLE_HANDLER 等）、CSRF 保護、セッション管理 | Apache 2.0 | GA（Spring Boot に同梱） |
| MyBatis | 3.x | データアクセス | XML マッパーによる SQL の明示的管理、CQRS の Read Model クエリ最適化との親和性 | Apache 2.0 | GA（mybatis-spring-boot-starter 4.0.1 で Spring Boot 4 対応済み） |
| Spring Events | - | ドメインイベント発行 | `ApplicationEventPublisher` による CDI Events の代替、疎結合なコンテキスト間通信 | Apache 2.0 | GA（Spring Boot に同梱） |
| Thymeleaf Security | 3.x | テンプレートへの権限連携 | `sec:authorize` タグによるロール別 UI 制御 | Apache 2.0 | GA（Spring Boot に同梱） |

> **バージョン採用方針**: Java 25 / Spring Boot 4.0 はリリース直後であるため、エコシステムの成熟状況を監視しながら採用する。
> プロジェクト開始時点で GA が不安定な場合は、Java 21 LTS + Spring Boot 3.4.x で開発を開始し、
> Spring Boot 4.0 GA リリース後に移行するロードマップを ADR に記録する。
> 詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Thymeleaf | 3.x | テンプレートエンジン（SSR） | Spring Boot との統合、サーバーサイドレンダリングによるシンプルな構成、SEO 対応 | Apache 2.0 | GA（安定版） |
| Bootstrap | 5.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |
| Alpine.js | 3.x | 最小 JavaScript | モーダル・ドロップダウン等の軽量 JS インタラクション（htmx の補完） | MIT | GA（アクティブ開発中） |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| H2 | 2.x | テスト・開発用インメモリ DB | MyBatis 互換、テスト実行の高速化、セットアップ不要 | MPL 2.0 / EPL 1.0 | GA（アクティブ開発中） |
| Flyway | 10.x | DB マイグレーション | バージョン管理されたスキーマ変更、Spring Boot 統合、コンテキスト別マイグレーション管理 | Apache 2.0 | GA（Community Edition） |

> **テスト環境の DB 設定**: H2 は PostgreSQL 互換モード（`MODE=PostgreSQL`）で使用する。
> `spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| JUnit 5 | 5.x | テストフレームワーク | Java 標準のテストフレームワーク、パラメータ化テスト・入れ子テストクラス対応 | EPL 2.0 | GA（アクティブ開発中） |
| Mockito | 5.x | モックライブラリ | Spring Boot Test との統合、ドメインサービス・ポートのモック実装 | MIT | GA（アクティブ開発中） |
| AssertJ | 3.x | アサーションライブラリ | 流暢な API、集約・値オブジェクトのテストコードの可読性向上 | Apache 2.0 | GA（アクティブ開発中） |
| Testcontainers | 2.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、Spring Boot 4 の `@ServiceConnection` 対応 | Apache 2.0 | GA（2.0 リリース済み） |
| Spring MockMvc | - | Controller テスト | Spring MVC エンドポイントのテスト、Thymeleaf テンプレートのレンダリング検証 | Apache 2.0 | GA（Spring Boot に同梱） |
| ArchUnit | 1.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（ドメイン層がインフラ層に依存しないこと等） | Apache 2.0 | GA（アクティブ開発中） |
| WireMock | 3.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ | Apache 2.0 | GA（アクティブ開発中） |
| Playwright | 1.44+ | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |

> **ArchUnit 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（`domain` パッケージが `infrastructure` パッケージを import しない）
> 2. ドメイン層に Spring アノテーションを使用しないこと（`@Component`, `@Service`, `@Repository` 等）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port 経由で参照する）
> 4. 異なる Bounded Context 間でクラスを直接参照しないこと（ACL/Event 経由のみ）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Gradle | 9.x | ビルドツール | 柔軟なビルド設定・依存関係管理・Spring Boot 4 プラグイン対応 | Apache 2.0 | GA（9.2.1 使用中） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| Checkstyle | - | Java コードスタイルチェック | コーディング規約の自動チェック | LGPL 2.1 | GA（アクティブ開発中） |
| SpotBugs | - | Java 静的解析 | バグパターンの自動検出、ドメインオブジェクトの null 安全性チェック | LGPL 2.1 | GA（アクティブ開発中） |

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

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | Spring WebClient / WireMock（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | Spring WebClient / WireMock（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| Spring WebClient / WireMock（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | Spring WebClient / WireMock（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | Spring WebClient / WireMock（テスト） | `NotificationPort` |

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Java: LTS バージョン（Java 21）を実績ベースとし、Java 25 LTS リリース時に移行する
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

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポートとして抽象化し、Spring WebClient と WireMock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: ArchUnit によるアーキテクチャルールの自動検証を追加し、ヘキサゴナルアーキテクチャの依存関係制約が
   コードベースに継続的に適用されることを保証する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。
