---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: マイクロサービス・DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-08-19T00:00:00.000Z
tags: design, tech-stack, java, spring-boot, react, microservices
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（マイクロサービス + DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（React SPA + TanStack Query + Zustand + Tailwind CSS）、
インフラアーキテクチャ（ローカル: kind + Kustomize ／ 開発: Heroku ／ ステージング・本番: AWS ECS Fargate + RDS PostgreSQL + Amazon MQ）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

take-3 の技術スタックを基礎とし、本プロジェクトの差分として **ローカル実行基盤の kind + Kustomize への変更（ADR-002）** と **開発環境の Heroku Container Registry / Runtime + CloudAMQP の採用（ADR-003）** を反映している。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Java | 25 | アプリケーション実装言語 | 豊富なエコシステム、Spring Boot との親和性、パターンマッチング等の最新機能活用 | Oracle Free Terms | GA |
| Spring Boot | 4.x | アプリケーションフレームワーク | 自動構成による開発効率、マイクロサービス構築の標準、DDD 実装との親和性、Spring Framework 7.x 基盤 | Apache 2.0 | GA |
| Spring Cloud Gateway | 4.x | API Gateway | マイクロサービスへのルーティング、JWT フィルタ、CORS 制御、ロードバランシング | Apache 2.0 | GA |
| Spring Cloud Stream | 4.x | メッセージング抽象化 | RabbitMQ バインダーによるイベント駆動通信、マイクロサービス間の非同期連携 | Apache 2.0 | GA |
| Spring Security | 7.x | 認証・認可 | JWT Bearer Token 認証、RBAC、API Gateway でのトークン検証、アカウントロック（US31） | Apache 2.0 | GA |
| MyBatis | 3.x | データアクセス | XML マッパーによる SQL の明示的管理、CQRS の Read Model クエリ最適化、ドメインモデルへの永続化関心の非混入 | Apache 2.0 | GA |
| Spring MVC | 7.x | REST API フレームワーク | REST Controller によるヘキサゴナルの Primary Adapter 実装 | Apache 2.0 | GA |
| Spring Events | - | ドメインイベント発行 | `ApplicationEventPublisher` による疎結合なコンテキスト内通信 | Apache 2.0 | GA |
| springdoc-openapi | 2.x | API ドキュメント（Swagger UI） | REST API の自動ドキュメント生成、各マイクロサービスの API 仕様公開 | Apache 2.0 | GA |

> **バージョン採用方針**: Java 25 は最新機能を活用するために採用する。Spring Boot 4.x（Spring Framework 7.x 基盤）を使用する。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| React | 19.x | SPA フレームワーク | コンポーネントベース設計、Hooks による状態管理、大規模エコシステム | MIT | GA |
| Vite | 6.x | ビルドツール | 高速な HMR、ESM ベースのバンドル、React プラグイン統合 | MIT | GA |
| TypeScript | 5.x | 型付き JavaScript | 型安全性による開発効率向上、API 型定義との整合性 | Apache 2.0 | GA |
| TanStack Query | 5.x | サーバー状態管理 | API データのキャッシュ・同期・ポーリング（追跡照会 30 秒間隔）、楽観的更新 | MIT | GA |
| Zustand | 5.x | クライアント状態管理 | 軽量な状態管理、JWT トークン・UI 状態の管理、React Query との共存 | MIT | GA |
| Tailwind CSS | 4.x | ユーティリティファースト CSS | 迅速なスタイリング、カスタマイズ性、業務ツール向けの実用的デザイン | MIT | GA |
| React Router | 7.x | クライアントサイドルーティング | SPA のページ遷移、ネストルート、ガード（認証チェック・公開追跡の除外） | MIT | GA |
| fetch (built-in) | - | HTTP クライアント | ブラウザ組み込み API、外部依存なし、API Gateway 経由で全サービスに接続 | - | Web 標準 |
| React Hook Form | 7.x | フォーム管理 | パフォーマンス最適化されたフォーム、バリデーション統合 | MIT | GA |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | ローカル・ステージング・本番用 RDBMS（6 DB） | ACID 準拠、JSON 型サポート、Database per Service パターンでの運用実績 | PostgreSQL License | GA（EOL: 2028-11） |
| H2 | 2.x | 開発環境（Heroku）・テスト用インメモリ DB | MyBatis 互換、テスト実行の高速化、PostgreSQL 互換モード | MPL 2.0 / EPL 1.0 | GA |
| Flyway | 10.x | DB マイグレーション | バージョン管理されたスキーマ変更、サービスごとの独立マイグレーション管理 | Apache 2.0 | GA（Community Edition） |

> **H2 の設定**: PostgreSQL 互換モード（`MODE=PostgreSQL`）で使用する。
> `spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`
> 全マイグレーション SQL は CI で H2 / PostgreSQL の両方に対して実行できることを確認する（方言差は両方向に起きる）。

## メッセージング

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RabbitMQ | 3.x | メッセージブローカー（ローカル: kind 内コンテナ） | Spring Cloud Stream のデフォルトバインダー、マイクロサービス間のドメインイベント配信 | MPL 2.0 | GA |
| CloudAMQP | - | マネージド RabbitMQ（開発環境 / Heroku アドオン） | AMQPS/TLS 接続、無料プランで結合テスト環境を低コスト運用（ADR-003） | - | GA（マネージド） |
| Amazon MQ | - | マネージド RabbitMQ（ステージング・本番） | AWS マネージドによる運用負荷軽減、Multi-AZ 対応 | - | GA（AWS マネージド） |

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| JUnit 5 | 5.x | バックエンドテストフレームワーク | Java 標準、パラメータ化テスト・入れ子テストクラス対応 | EPL 2.0 | GA |
| Mockito | 5.x | バックエンドモックライブラリ | Spring Boot Test との統合、ドメインサービス・ポートのモック実装 | MIT | GA |
| AssertJ | 3.x | バックエンドアサーションライブラリ | 流暢な API、集約・値オブジェクトのテストコードの可読性向上 | Apache 2.0 | GA |
| Testcontainers | 1.x | 統合テスト用コンテナ | 実 PostgreSQL / RabbitMQ を使用した統合テスト、Spring Boot の `@ServiceConnection` 対応 | Apache 2.0 | GA |
| Spring Cloud Contract | 4.x | 契約テスト | マイクロサービス間の REST API / イベント契約検証、Consumer-Driven Contract | Apache 2.0 | GA |
| ArchUnit | 1.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証 | Apache 2.0 | GA |
| WireMock | 3.x | 外部 API スタブ | 外部システムポート（PaymentGatewayPort 等）のスタブ、サービス間 API モック | Apache 2.0 | GA |
| Vitest | 3.x | フロントエンドユニットテスト | Vite ネイティブ、高速実行、Custom Hooks テスト | MIT | GA |
| Testing Library | 16.x | フロントエンドコンポーネントテスト | ユーザー視点のコンポーネントテスト、アクセシビリティ検証 | MIT | GA |
| MSW | 2.x | フロントエンド API モック | Service Worker ベースの API モック、TanStack Query との統合 | MIT | GA |
| Playwright | 1.x | E2E テスト | クロスブラウザ E2E テスト、SPA のページ遷移・ポーリングテスト | Apache 2.0 | GA |
| JaCoCo | 0.8.x | バックエンドカバレッジ | コードカバレッジ測定、Quality Gate との連携 | EPL 2.0 | GA |

> **ArchUnit 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと
> 2. ドメイン層に Spring アノテーションを使用しないこと
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port 経由で参照する）
> 4. 異なるマイクロサービスのパッケージを直接参照しないこと（越境は ACL ポートのみ）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Gradle | 9.x | バックエンドビルドツール | マルチプロジェクト構成（7 サービス + shared）、Spring Boot プラグイン対応 | Apache 2.0 | GA |
| npm | 10.x | フロントエンドパッケージ管理 | Node.js 標準、Vite プロジェクトとの統合 | Artistic 2.0 | GA |
| GitHub Actions | - | CI/CD パイプライン | サービス単位のパスフィルタによる並列ビルド、OIDC 認証による AWS デプロイ、Heroku へのデプロイ | - | GA |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| Checkstyle | - | Java コードスタイルチェック | コーディング規約の自動チェック | LGPL 2.1 | GA |
| SpotBugs | - | Java 静的解析 | バグパターンの自動検出 | LGPL 2.1 | GA |
| ESLint | 9.x | フロントエンド静的解析 | TypeScript / React のコード品質チェック | MIT | GA |
| Prettier | 3.x | フロントエンドフォーマッター | コードスタイルの自動統一 | MIT | GA |
| Trivy | - | セキュリティスキャン | コンテナイメージ・依存の脆弱性検査。公式イメージの直接実行で導入失敗と検出を区別する | Apache 2.0 | GA |
| JIG | 2026.7.4 | 設計ドキュメント生成 | バイトコードから実装の構造を出力し、docs/design（設計）との乖離を生成物として検出する | Apache 2.0 | GA |
| jig-erd | 0.2.2 | ER 図生成 | Flyway が構築した実スキーマから ER 図を出力し、data-model.md（設計）との乖離を検出する。Database per Service のためサービス単位で生成される | Apache 2.0 | GA |

## インフラ

### ローカル開発環境（ADR-002）

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 27.x | コンテナ化 | 環境の再現性、マルチステージビルドによる本番イメージ最小化 | Apache 2.0 | GA |
| kind | 0.2x | ローカル Kubernetes クラスタ | クラスタの作成・破棄が速く CI と同一手段を使える。`kind load docker-image` でローカルイメージを直接ロード | Apache 2.0 | GA |
| kubectl | 1.3x | Kubernetes CLI | マニフェスト適用・状態確認。Kustomize を同梱 | Apache 2.0 | GA |
| Kustomize | kubectl 同梱 | マニフェスト合成 | base / overlay 構成でテンプレート言語なしに環境差分を管理。ローカルと CI で base を共有 | Apache 2.0 | GA |
| NGINX Ingress Controller | 1.x | ローカル Ingress | フロントエンド・Gateway へのルーティング | Apache 2.0 | GA |

### 開発環境（結合テスト / ADR-003）

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Heroku Container Registry / Runtime | - | コンテナ実行環境（container stack） | サービスごとに個別アプリとしてデプロイ。低コストで常時稼働の結合テスト環境を維持 | - | GA |
| Heroku CLI | 最新 | ビルド済みイメージの push / release | `heroku container:push` / `release` によるデプロイ自動化 | MIT | GA |
| CloudAMQP | - | マネージド RabbitMQ（Heroku アドオン） | AMQPS/TLS でのイベント配信。無料プランで賄う | - | GA |

> Heroku Container Runtime は `x86_64` のみ対応。Apple Silicon では `linux/amd64` でビルドする。

### ステージング・本番環境

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、サービスごとの独立スケーリング、運用負荷軽減 | - | GA |
| AWS RDS PostgreSQL | 16.x | マネージド DB（6 DB） | Multi-AZ 自動フェイルオーバー、自動バックアップ、PITR | - | GA |
| Amazon MQ | - | マネージド RabbitMQ | クラスター構成、マルチ AZ 対応、サービス間イベント配信 | - | GA |
| AWS ALB | - | ロードバランサー | HTTPS 終端、ヘルスチェック、Blue/Green デプロイ対応 | - | GA |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・JWT シークレットの安全な管理 | - | GA |
| AWS CloudWatch | - | 監視・ログ | アプリケーションログ・メトリクス・アラートの統合管理 | - | GA |
| AWS Route 53 | - | DNS | ドメイン管理、ヘルスチェックフェイルオーバー | - | GA |
| AWS ACM | - | TLS 証明書 | HTTPS 証明書の自動更新 | - | GA |

## ドキュメント

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs | 1.x | ドキュメントサイト生成 | Markdown ベース、Material テーマ、PlantUML 統合 | BSD 2-Clause | GA |
| PlantUML | - | ダイアグラム生成 | UML 図・ER 図・ワイヤーフレームのコードベース管理 | GPL 3.0 | GA |
| Mermaid | 11.x | ダイアグラム生成 | Markdown 内インライン図表（Quadrant Chart 等） | MIT | GA |

## 開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| IntelliJ IDEA | - | IDE | Java / Spring Boot 開発の標準 IDE、DDD パターン対応リファクタリング支援 | Commercial / Community | GA |
| VS Code | - | IDE | フロントエンド（React / TypeScript）開発、Tailwind CSS IntelliSense | MIT | GA |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、フロントエンドビルド | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA |

## 外部システム連携技術

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 | 使用サービス |
| :--- | :--- | :--- | :--- | :--- |
| 経路設計サービス（routingms） | REST API（HTTP/JSON） | Spring RestClient / WireMock（テスト） | `ExternalRoutingServicePort` | bookingms |
| 決済機関 | REST API（HTTPS） | Spring RestClient / WireMock（テスト） | `PaymentGatewayPort` | billingms |
| 港湾管理システム | REST API（HTTP/JSON） | Spring RestClient / WireMock（テスト） | `PortManagementPort` | handlingms |
| 通知システム | REST API（HTTP/JSON） | Spring RestClient / WireMock（テスト） | `NotificationPort` | bookingms / trackingms / billingms |

> 税関システムとの電子連携は行わない。通関状態は担当者による手入力とする（UC21）。

## バージョン管理方針

### LTS 優先選定

- Java: 25 を採用。次期 LTS リリース時に移行を検討する
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- Spring Boot: 4.x のマイナーバージョンは積極的に追従する
- React: 19.x のマイナーバージョンは積極的に追従する
- Node.js: LTS バージョン（22.x）を使用する
- kind / kubectl: kubectl のサポートスキュー（±1 マイナー）の範囲で追従する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Java | 25 | 次 LTS | 2027 年 | JVM 設定、ライブラリ互換性 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Spring Boot | 4.x | 4.x 最新 | 随時 | マイナーバージョンに追従 |
| React | 19.x | 20.x | リリース後 | コンポーネント API 変更確認 |
| Node.js | 22.x | 24.x（次 LTS） | 2027 年 | ビルドスクリプト互換性 |
| kind / kubectl | 最新安定 | 随時 | 随時 | マニフェスト API バージョン確認 |

## 選定理由の総括

本システムの技術スタック選定は、以下の 6 方針に基づいている。

1. **マイクロサービスとの整合性**: Spring Cloud Gateway + Spring Cloud Stream で API ルーティングとイベント駆動通信を実現し、7 サービスの独立デプロイを支える技術基盤とした（ADR-001）。

2. **DDD + ヘキサゴナルの実現**: MyBatis による SQL の明示的管理はドメインモデルへの永続化関心の非混入を実現し、ArchUnit による依存関係ルールの自動検証でアーキテクチャ制約を継続的に保証する。

3. **SPA によるリッチな UX**: React + TanStack Query で追跡情報のリアルタイムポーリングや楽観的更新を実現し、Zustand で認証状態を一元管理する。fetch API の採用で外部依存を最小化した。

4. **テスト容易性**: サービス内はピラミッド型（JUnit + Testcontainers）、サービス間はダイヤモンド型（Spring Cloud Contract + WireMock）のハイブリッドテスト形状を技術的に支える。

5. **環境の 3 段構え**: ローカルは kind + Kustomize で Kubernetes の宣言的運用に習熟し（ADR-002）、開発環境は Heroku + CloudAMQP で低コストの常時結合テスト環境を維持し（ADR-003）、ステージング・本番は AWS マネージドサービスで SLA を満たす。

6. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ / Amazon MQ）を活用し、運用負荷を最小化する。CloudWatch による統合監視で Observability を確保する。

## 参照

- [バックエンドアーキテクチャ設計](architecture_backend.md)
- [フロントエンドアーキテクチャ設計](architecture_frontend.md)
- [インフラストラクチャアーキテクチャ設計](architecture_infrastructure.md)
- [ADR-001 マイクロサービスアーキテクチャ](../adr/001-microservices-architecture.md)
- [ADR-002 ローカル環境 kind + Kustomize](../adr/002-local-kubernetes-kustomize.md)
- [ADR-003 開発環境 Heroku](../adr/003-heroku-development-environment.md)
