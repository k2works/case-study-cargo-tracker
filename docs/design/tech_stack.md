---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-07-04T00:00:00.000Z
tags: design, tech-stack, csharp, aspnet-core, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（Razor SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| C# | 14 | アプリケーション実装言語 | record / init プロパティによる不変な値オブジェクト表現、null 許容参照型による null 安全性、.NET 10 との親和性 | MIT | GA（.NET 10 同梱） |
| .NET | 10.0 (LTS) | ランタイム・SDK | 長期サポート（EOL: 2028-11）、豊富なエコシステム、クロスプラットフォーム対応 | MIT | GA（LTS） |
| ASP.NET Core | 10.0 | アプリケーションフレームワーク | ミドルウェアパイプラインと DI 標準統合による開発効率、DDD 実装との親和性 | MIT | GA（.NET 10 に同梱） |
| ASP.NET Core MVC | 10.0 | Web フレームワーク | Razor ビュー Controller・REST API Controller の統合、ヘキサゴナルの Primary Adapter として機能 | MIT | GA（.NET 10 に同梱） |
| ASP.NET Core Cookie 認証 | 10.0 | 認証・認可 | Cookie ベースのフォーム認証、ロール Claim による RBAC、CSRF 保護（Antiforgery）、セッション管理。full Identity（EF Core）は導入せず Dapper 軽量ユーザーストア + `PasswordHasher` を用いる（ADR-0004） | MIT | GA（.NET 10 に同梱） |
| Dapper | 2.x | データアクセス（マイクロ ORM） | 手書き SQL による完全な制御と高速なマッピング、DDD リポジトリ実装との親和性、CQRS Read Model の DTO 直接射影 | Apache 2.0 | GA（アクティブ開発中） |
| MediatR | 12.x | ドメインイベント発行 / CQRS | Command / Query / Notification による CQRS 実装、`INotification` によるドメインイベントの疎結合なコンテキスト間通信 | Apache 2.0 | GA（アクティブ開発中） |
| Swashbuckle.AspNetCore | 6.x | API ドキュメント（Swagger UI） | REST API の自動ドキュメント生成、環境別（Development のみ）有効化 | MIT | GA（アクティブ開発中） |
| Microsoft.Extensions.DependencyInjection | 10.0 | DI コンテナ | .NET 標準の DI、コンストラクタインジェクションによる Port/Adapter の結線 | MIT | GA（.NET 10 に同梱） |
| DataAnnotations + FluentValidation | 10.0 / 11.x | 入力バリデーション | DataAnnotations による基本検証と FluentValidation による複合ルールの流暢な定義 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| Serilog | 4.x | 構造化ログ | 構造化ロギング、Sink による出力先の柔軟な切替（Console / CloudWatch） | Apache 2.0 | GA（アクティブ開発中） |
| ASP.NET Core Health Checks | 10.0 | ヘルスチェック | `/health` エンドポイントによる Liveness / Readiness 検査、ALB ヘルスチェック・ECS との統合 | MIT | GA（.NET 10 に同梱） |

> **バージョン採用方針**: .NET 10 は LTS（2025-11 リリース、EOL: 2028-11）であり安定運用が可能である。
> 次期 LTS（.NET 12）への移行はエコシステム（Dapper・Npgsql・Testcontainers 等）の対応状況を監視しながら計画し、
> 移行ロードマップを ADR に記録する。
> 詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Razor ビュー (ASP.NET Core MVC) | 10.0 | テンプレートエンジン（SSR） | ASP.NET Core との統合、サーバーサイドレンダリングによるシンプルな構成、SEO 対応、Tag Helper による型安全なフォーム生成 | MIT | GA（.NET 10 に同梱） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |
| LibMan | 3.x | クライアントライブラリ管理 | CDN からの静的アセット（Bootstrap / htmx）取得と `wwwroot/lib` への配置を宣言的に管理 | MIT | GA |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番・ステージング用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| SQLite | 3.x | 開発環境用 RDBMS | ファイルベースでセットアップ不要、開発マシンで軽量に動作、Microsoft.Data.Sqlite（ADO.NET）経由で Dapper から利用 | Public Domain | GA（アクティブ開発中） |
| Npgsql | 10.x | ADO.NET プロバイダ（PostgreSQL） | PostgreSQL 16 への接続を担う .NET 標準の ADO.NET プロバイダ、配列型・JSONB 等の PostgreSQL 固有機能対応 | PostgreSQL License | GA（アクティブ開発中） |
| Microsoft.Data.Sqlite | 10.x | ADO.NET プロバイダ（SQLite） | 開発環境の SQLite 接続、Dapper は ADO.NET 抽象（`IDbConnection`）経由で両プロバイダを透過的に利用 | MIT | GA（アクティブ開発中） |
| DbUp | 6.x | DB マイグレーション | バージョン付き SQL スクリプトによる forward-only マイグレーション、journal テーブルによる適用管理、Flyway と同思想。PostgreSQL / SQLite 両対応 | MIT | GA（アクティブ開発中） |

> **環境別の DB 設定**: 開発環境は SQLite（ファイル DB）で軽量に動作させ、ステージング・本番は PostgreSQL を使用する。
> 両方言で動作させるため、リポジトリの SQL は ANSI 標準の範囲を基本とし、PostgreSQL 固有機能（JSONB・配列型等）は使用しない。
> 方言差異が避けられない箇所（`NOW()` / `BIGSERIAL` 等）は接続ファクトリと DbUp スクリプトのプロバイダ別ディレクトリで吸収する。
> データアクセスを検証するテストは Testcontainers for .NET による実 PostgreSQL で実施し、本番との差異を排除する。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| xUnit | 2.9.x | テストフレームワーク | .NET 標準のテストフレームワーク、`[Theory]` によるパラメータ化テスト・クラスフィクスチャ対応 | Apache 2.0 | GA（アクティブ開発中） |
| Moq | 4.20.x | モックライブラリ | ドメインサービス・ポートのモック実装、xUnit との統合 | BSD 3-Clause | GA（アクティブ開発中） |
| FluentAssertions | 6.12.x | アサーションライブラリ | 流暢な API、集約・値オブジェクトのテストコードの可読性向上 | Apache 2.0 | GA（アクティブ開発中） |
| Testcontainers for .NET | 3.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、`PostgreSqlContainer` モジュールによる簡潔なセットアップ | MIT | GA（アクティブ開発中） |
| WebApplicationFactory | 10.0 | Controller / エンドポイントテスト | ASP.NET Core エンドポイントのインメモリテスト、Razor ビューのレンダリング検証 | MIT | GA（.NET 10 に同梱） |
| ArchUnitNET | 0.11.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（ドメイン層がインフラ層に依存しないこと等） | Apache 2.0 | GA（アクティブ開発中） |
| WireMock.Net | 1.6.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ | Apache 2.0 | GA（アクティブ開発中） |
| Microsoft.Playwright | 1.4x | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため（.NET 版バインディング） | Apache 2.0 | GA（アクティブ開発中） |
| coverlet + ReportGenerator | 6.x / 5.x | カバレッジ計測・レポート | `dotnet test` 統合によるカバレッジ収集（Cobertura 形式）、HTML レポート生成、SonarQube 連携 | MIT / Apache 2.0 | GA（アクティブ開発中） |

> **ArchUnitNET 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（`Domain` 名前空間が `Infrastructure` 名前空間を参照しない）
> 2. ドメイン層にフレームワーク属性を使用しないこと（ASP.NET Core の属性・Dapper 等の技術的関心事）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port 経由で参照する）
> 4. 異なる Bounded Context 間でクラスを直接参照しないこと（ACL/Event 経由のみ）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| dotnet CLI / MSBuild | 10.0.x | ビルドツール | ソリューション（`CargoTracker.sln`）単位のビルド・テスト・発行、`Directory.Build.props` による共通設定管理 | MIT | GA（.NET 10 に同梱） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保（SonarScanner for .NET） | LGPL 3.0 | GA（Community Edition） |
| .NET Analyzers + StyleCop.Analyzers | 8.0 / 1.2.x | C# コードスタイルチェック | Roslyn アナライザーによるコーディング規約の自動チェック、`.editorconfig` によるルール管理 | MIT | GA（アクティブ開発中） |
| dotnet format | 10.0.x | コードフォーマッタ | `.editorconfig` 準拠の自動フォーマット、CI での検証 | MIT | GA（.NET 10 に同梱） |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | 環境の再現性、開発・本番環境の一貫性、マルチステージビルド（SDK → runtime イメージ）による本番イメージ最小化 | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（アプリ + PostgreSQL + SonarQube）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・API キーの安全な管理、.NET Configuration プロバイダとの統合 | - | GA（AWS マネージド） |
| AWS CloudWatch | - | 監視・ログ | アプリケーションログ（Serilog Sink 経由）・メトリクス・アラートの統合管理 | - | GA（AWS マネージド） |
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
| JetBrains Rider / Visual Studio | - | IDE | C# / ASP.NET Core 開発の標準 IDE、DDD パターン対応リファクタリング支援 | Commercial / Community | GA（JetBrains / Microsoft サポート） |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `IExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `ICustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `IPaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `IPortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `INotificationPort` |

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- .NET: LTS バージョン（.NET 10）を採用し、次期 LTS（.NET 12）への移行はエコシステム対応を確認して計画する
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- ASP.NET Core: .NET のメジャーバージョンに追従し、パッチバージョンは積極的に更新する。Dapper / DbUp / Npgsql はマイナーバージョンを定期的に更新する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| .NET | 10.0 (LTS) | 12.0（次期 LTS） | 2028 年 | ランタイム設定、NuGet パッケージ互換性 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Dapper / DbUp | 2.x / 6.x | 次期メジャー | メジャー変更時 | マッピング API・スクリプト適用処理の互換性確認 |
| MediatR | 12.x | 13.x | メジャー変更時 | ハンドラ登録・ライセンス条件の確認 |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を .NET エコシステムで自然に実現できる技術を優先した。
   特に MediatR による Command / Query の分離と、Dapper による DTO への直接射影クエリは CQRS の Read Model 最適化に適合する。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポートとして抽象化し、IHttpClientFactory と WireMock.Net の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: ArchUnitNET によるアーキテクチャルールの自動検証を追加し、ヘキサゴナルアーキテクチャの依存関係制約が
   コードベースに継続的に適用されることを保証する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。
