---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく Go 版技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-07-10T00:00:00.000Z
tags: design, tech-stack, go, chi, htmx, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システム（Go 版）で採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（html/template SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Go | 1.24.x | アプリケーション実装言語 | シンプルな言語仕様、静的バイナリによるデプロイ容易性、高速なビルド・テスト、標準ライブラリの充実 | BSD 3-Clause | GA（直近 2 マイナーバージョンをサポート） |
| chi | v5 | HTTP ルーター | 標準 `net/http` 互換のミドルウェアチェーン、依存最小、ヘキサゴナルの Primary Adapter として機能 | MIT | GA（アクティブ開発中） |
| net/http | 標準 | Web サーバー・HTTP クライアント | Go 標準ライブラリ、フレームワークロックインの回避、長期安定性 | BSD 3-Clause | GA（Go 本体に同梱） |
| html/template | 標準 | テンプレートエンジン（SSR） | Go 標準の SSR、コンテキスト依存の自動エスケープによる XSS 対策、Thymeleaf SSR の代替 | BSD 3-Clause | GA（Go 本体に同梱） |
| alexedwards/scs | v2 | セッション管理 | セッションベース認証の実装基盤、PostgreSQL ストア対応、Spring Security のセッション管理の代替 | MIT | GA（アクティブ開発中） |
| justinas/nosurf | v1 | CSRF 保護ミドルウェア | フォーム・htmx リクエストの CSRF トークン検証を `net/http` ミドルウェアとして提供、Spring Security の CSRF 保護の代替 | MIT | GA（安定版） |
| go-playground/validator | v10 | 入力バリデーション | 構造体タグ（`validate:"required"` 等）による宣言的なサーバーサイド検証、Bean Validation の代替 | MIT | GA（アクティブ開発中） |
| microcosm-cc/bluemonday | v1 | HTML サニタイズ（XSS 対策） | ユーザー入力を HTML として出力する際のサニタイズ、html/template の自動エスケープを補完 | MIT | GA（アクティブ開発中） |
| 自作 RBAC ミドルウェア | - | 認可（RBAC） | ROLE_SALES / ROLE_HANDLER 等のロール別アクセス制御を chi ミドルウェアとして実装、Spring Security の認可の代替 | プロジェクト内 | 自作（テストで保証） |
| 自作イベントディスパッチャ | - | ドメインイベント発行 | in-process のイベント発行・購読による疎結合なコンテキスト間通信、Spring Events（`ApplicationEventPublisher`）の代替 | プロジェクト内 | 自作（テストで保証） |
| swaggo/swag | v2 | API ドキュメント（Swagger UI） | コメントアノテーションからの OpenAPI 自動生成、springdoc-openapi の代替、環境別有効化に対応 | Apache 2.0 | GA（アクティブ開発中） |

> **バージョン採用方針**: Go は直近 2 マイナーバージョンのみ公式サポートされるため、1.24.x を起点とし
> 新マイナーリリース後は速やかに追従する。言語・標準ライブラリは Go 1 互換性保証により移行リスクが小さい。
> 詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| html/template | 標準 | テンプレートエンジン（SSR） | Go 標準ライブラリによるサーバーサイドレンダリング、シンプルな構成、SEO 対応 | BSD 3-Clause | GA（Go 本体に同梱） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番・テスト用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| pgx | v5 | PostgreSQL ドライバ | 高性能な PostgreSQL ネイティブドライバ、コネクションプール（pgxpool）、型マッピングの柔軟性 | MIT | GA（アクティブ開発中） |
| sqlc | 1.x | 型安全 SQL コード生成 | SQL を明示的に管理しつつ型安全な Go コードを生成、CQRS の Read Model クエリ最適化との親和性（MyBatis の選定理由を継承） | MIT | GA（アクティブ開発中） |
| golang-migrate | v4 | DB マイグレーション | バージョン管理されたスキーマ変更、CLI / ライブラリ両対応、コンテキスト別マイグレーション管理 | MIT | GA（アクティブ開発中） |

> **テスト環境の DB 設定**: H2 のようなインメモリ DB は使用せず、testcontainers-go による実 PostgreSQL に一本化する。
> 本番と同一のデータベースエンジンでテストすることで、SQL 方言差異による不具合を排除する。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| testing | 標準 | テストフレームワーク | Go 標準のテスト機構、テーブル駆動テスト・サブテスト（`t.Run`）・並列実行対応 | BSD 3-Clause | GA（Go 本体に同梱） |
| testify | 1.x | アサーション・モック補助 | 流暢なアサーション API（assert / require）、集約・値オブジェクトのテストコードの可読性向上 | MIT | GA（アクティブ開発中） |
| testcontainers-go | 0.3x | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、H2 を使わず本番同等環境でのテストに一本化 | MIT | GA（アクティブ開発中） |
| moq | 0.x | ポートのモック生成 | インターフェース（ポート）からのモック自動生成、`go generate` 統合、ドメインサービス・ポートのモック実装 | MIT | GA（アクティブ開発中） |
| go-arch-lint | 1.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（ArchUnit の代替）、YAML でルール定義し CI で検証 | MIT | GA（アクティブ開発中） |
| httptest | 標準 | HTTP テスト・外部 API スタブ | ハンドラーテストおよび ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ（WireMock の代替） | BSD 3-Clause | GA（Go 本体に同梱） |
| Playwright | 1.44+ | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |

> **go-arch-lint 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（`domain` パッケージが `infrastructure` パッケージを import しない）
> 2. ドメイン層が外部フレームワーク・ドライバ（chi、pgx 等）に依存しないこと（標準ライブラリのみ許可）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port インターフェース経由で参照する）
> 4. 異なる Bounded Context 間でパッケージを直接参照しないこと（ACL/Event 経由のみ）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Go toolchain | 1.24.x | ビルドツール | `go build` / `go test` / `go vet` による標準ワークフロー、静的バイナリ生成、クロスコンパイル対応 | BSD 3-Clause | GA（Go 本体に同梱） |
| Make | - | タスク定義 | ビルド・テスト・コード生成（sqlc / moq / swag）の統一エントリポイント | GPL 3.0 | GA（安定版） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| golangci-lint | 1.x | Go 静的解析・スタイルチェック | 多数の linter の統合実行（Checkstyle / SpotBugs の代替）、コーディング規約の自動チェック、CI 統合 | GPL 3.0 | GA（アクティブ開発中） |
| go test -cover | 標準 | カバレッジ計測 | 標準ツールによるカバレッジ計測、SonarQube へのレポート連携 | BSD 3-Clause | GA（Go 本体に同梱） |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | 環境の再現性、開発・本番環境の一貫性、マルチステージビルド + distroless による本番イメージ最小化（静的バイナリと好相性） | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（アプリ + PostgreSQL + SonarQube）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・API キーの安全な管理、起動時取得によるアプリ統合 | - | GA（AWS マネージド） |
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
| GoLand / VS Code | - | IDE | Go 開発の標準 IDE、リファクタリング支援・デバッガ・gopls 統合 | Commercial / MIT | GA（JetBrains / Microsoft サポート） |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | net/http Client / httptest（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | net/http Client / httptest（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| net/http Client / httptest（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | net/http Client / httptest（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | net/http Client / httptest（テスト） | `NotificationPort` |

## バージョン管理方針

### 安定版優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Go: 直近 2 マイナーバージョンのみ公式サポートされるため、1.24.x を起点に新マイナーリリースへ随時追従する（Go 1 互換性保証により移行リスクは小さい）
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- サードパーティライブラリ（chi / pgx / scs / testify 等）: Dependabot によるパッチ・マイナー追従を基本とし、メジャーバージョンアップは ADR で判断する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Go | 1.24.x | 1.25.x / 1.26.x | リリース後随時 | toolchain 更新、golangci-lint 対応確認 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| pgx / sqlc | v5 / 1.x | 最新マイナー | 随時 | 生成コードの再生成・差分確認 |
| golang-migrate | v4 | v5（リリース時） | メジャー変更時 | マイグレーションスクリプト |
| htmx | 2.0.x | 2.x 最新 | 随時 | 部分更新画面の回帰テスト |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Go の標準ライブラリと最小限の依存で自然に実現できる技術を優先した。
   特に sqlc による SQL の明示的管理と型安全なコード生成は CQRS の Read Model 最適化に適合する（MyBatis の選定理由を継承）。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポート（Go インターフェース）として抽象化し、標準 net/http Client と
   httptest の組み合わせで実装・テストを完結できる構成とした。

3. **テスト容易性**: go-arch-lint によるアーキテクチャルールの自動検証と、testcontainers-go による実 PostgreSQL 統合テストへの一本化により、
   ヘキサゴナルアーキテクチャの依存関係制約と本番同等環境での検証がコードベースに継続的に適用されることを保証する。

4. **運用保守性**: 静的バイナリ + distroless イメージによる小さく安全なコンテナと、AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、
   運用負荷を最小化しながら可用性要件（SLA 99.9%）を満たす構成とした。
