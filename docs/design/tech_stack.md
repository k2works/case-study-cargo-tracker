---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく F# 技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-07-06T00:00:00.000Z
tags: design, tech-stack, fsharp, giraffe, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS の関数型実装）、フロントエンドアーキテクチャ（Giraffe.ViewEngine SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

F# 選定の中核的な動機は、**判別共用体・レコードによる不変ドメインモデル**、**null 非許容がデフォルトの型システム**、
**Result 型による Railway Oriented Programming** が DDD の戦術的パターン（値オブジェクト・状態遷移・不変条件）を
最小のコード量で正確に表現できる点にある。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| F# | 9 | アプリケーション実装言語 | 判別共用体・レコードによる不変ドメインモデル、null 非許容がデフォルト、網羅的パターンマッチによる状態遷移の考慮漏れ検出、スマートコンストラクタで不正状態を型レベルで排除（Make Illegal States Unrepresentable） | MIT | GA（.NET 10 同梱） |
| .NET | 10.0 (LTS) | ランタイム・SDK | 長期サポート（EOL: 2028-11）、豊富なエコシステム、クロスプラットフォーム対応、C# 資産（ライブラリ）を F# からシームレスに利用可能 | MIT | GA（LTS） |
| ASP.NET Core | 10.0 | ホスティング基盤 | ミドルウェアパイプライン・Kestrel・Health Checks 等の実績ある基盤の上に Giraffe を載せる構成 | MIT | GA（.NET 10 に同梱） |
| Giraffe | 7.x | Web フレームワーク（HTTP ハンドラ DSL） | `HttpHandler` の関数合成（`>=>`）によるルーティング、ヘキサゴナルの Primary Adapter を純粋な F# 関数として記述可能、ASP.NET Core ミドルウェアとの完全互換 | Apache 2.0 | GA（アクティブ開発中） |
| Giraffe.ViewEngine | 2.x | HTML ビュー DSL（SSR） | F# 関数として型安全に HTML を生成、テンプレート言語不要でリファクタリング・コンパイル時検証が効く、ビューの合成 = 関数合成 | Apache 2.0 | GA（アクティブ開発中） |
| FsToolkit.ErrorHandling | 4.x | Result / Validation / ROP 支援 | `asyncResult` / `validation` CE によるワークフロー合成、適用的な全エラー収集、MediatR に頼らない CQRS ワークフロー実装の中核 | MIT | GA（アクティブ開発中） |
| Donald | 10.x | データアクセス（F# ADO.NET ラッパ） | 手書き SQL による完全な制御、F# レコードへの関数的マッピング、`IDbConnection` 抽象で Npgsql / SQLite を透過利用、CQRS Read Model の DTO 直接射影 | Apache 2.0 | GA（アクティブ開発中） |
| ASP.NET Core Cookie 認証 | 10.0 | 認証・認可 | Identity を使わず Giraffe の `requiresAuthentication` / `requiresRole` と直接統合できる軽量な Cookie 認証、RBAC（Sales / Handler 等のロール）、CSRF 保護 | MIT | GA（.NET 10 に同梱） |
| スマートコンストラクタ + FsToolkit Validation | - | 入力バリデーション | 型駆動バリデーション。値オブジェクト生成時に検証し `Result` で返すため、検証済みであることが型で保証される。DTO → Command 変換で全エラーを適用的に収集 | - | -（設計パターン） |
| Serilog | 4.x | 構造化ログ | 構造化ロギング、Sink による出力先の柔軟な切替（Console / CloudWatch） | Apache 2.0 | GA（アクティブ開発中） |
| ASP.NET Core Health Checks | 10.0 | ヘルスチェック | `/health` エンドポイントによる Liveness / Readiness 検査、ALB ヘルスチェック・ECS との統合 | MIT | GA（.NET 10 に同梱） |
| Swashbuckle.AspNetCore | 6.x | API ドキュメント（Swagger UI） | REST API のドキュメント生成、環境別（Development のみ）有効化 | MIT | GA（アクティブ開発中） |

> **MediatR を使用しない理由**: F# ではコマンド・クエリ・イベントを判別共用体で定義し、
> ハンドラを `Command -> Async<Result<Event, DomainError>>` の関数として部分適用で結線できるため、
> リフレクションベースのメディエータは冗長になる。関数合成の方が依存が明示的で、コンパイル時に結線ミスを検出できる。

> **バージョン採用方針**: .NET 10 は LTS（2025-11 リリース、EOL: 2028-11）であり安定運用が可能である。
> 次期 LTS（.NET 12）への移行はエコシステム（Giraffe・Donald・Npgsql・Testcontainers 等）の対応状況を監視しながら計画し、
> 移行ロードマップを ADR に記録する。詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Giraffe.ViewEngine | 2.x | テンプレートエンジン（SSR） | F# の関数としてビューを定義するためテンプレートと型の不整合が起きない、サーバーサイドレンダリングによるシンプルな構成、SEO 対応、パーシャル = ビュー関数の再利用 | Apache 2.0 | GA（アクティブ開発中） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化。部分 HTML は Giraffe.ViewEngine のビュー関数をそのまま返す | BSD 2-Clause | GA（アクティブ開発中） |
| 静的アセット管理（wwwroot 直置き） | - | クライアントライブラリ管理 | Bootstrap / htmx の 2 ライブラリのみのため、バージョン固定ファイルを `wwwroot/lib` に配置して管理（LibMan 相当の宣言管理は不要と判断） | - | - |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番・ステージング用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| SQLite | 3.x | 開発環境用 RDBMS | ファイルベースでセットアップ不要、開発マシンで軽量に動作、Microsoft.Data.Sqlite（ADO.NET）経由で Donald から利用 | Public Domain | GA（アクティブ開発中） |
| Npgsql | 10.x | ADO.NET プロバイダ（PostgreSQL） | PostgreSQL 16 への接続を担う .NET 標準の ADO.NET プロバイダ、Donald は `IDbConnection` 抽象経由で利用 | PostgreSQL License | GA（アクティブ開発中） |
| Microsoft.Data.Sqlite | 10.x | ADO.NET プロバイダ（SQLite） | 開発環境の SQLite 接続、Donald は ADO.NET 抽象（`IDbConnection`）経由で両プロバイダを透過的に利用 | MIT | GA（アクティブ開発中） |
| DbUp | 6.x | DB マイグレーション | バージョン付き SQL スクリプトによる forward-only マイグレーション、journal テーブルによる適用管理、Flyway と同思想。PostgreSQL / SQLite 両対応 | MIT | GA（アクティブ開発中） |

> **環境別の DB 設定**: 開発環境は SQLite（ファイル DB）で軽量に動作させ、ステージング・本番は PostgreSQL を使用する。
> 両方言で動作させるため、リポジトリの SQL は ANSI 標準の範囲を基本とし、PostgreSQL 固有機能（JSONB・配列型等）は使用しない。
> 方言差異が避けられない箇所（`NOW()` / `BIGSERIAL` 等）は接続ファクトリと DbUp スクリプトのプロバイダ別ディレクトリで吸収する。
> データアクセスを検証するテストは Testcontainers for .NET による実 PostgreSQL で実施し、本番との差異を排除する。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| xUnit | 2.9.x | テストフレームワーク | .NET 標準のテストフレームワーク、`[Theory]` によるパラメータ化テスト、F# から自然に利用可能 | Apache 2.0 | GA（アクティブ開発中） |
| FsUnit | 6.x | アサーションライブラリ（F# DSL） | `should equal` 等の F# らしい流暢なアサーション、判別共用体・レコードの構造的等価性テストと相性が良い | MIT | GA（アクティブ開発中） |
| FsCheck | 3.x | プロパティベーステスト | 値オブジェクトの不変条件・状態遷移の性質をランダム生成データで検証、スマートコンストラクタの網羅テストに最適 | BSD 3-Clause | GA（アクティブ開発中） |
| （モックライブラリ不使用） | - | Port のスタブ | Port が関数レコードのため、テストでは関数リテラルを差し込むだけでよく Moq 等のモックライブラリは不要 | - | -（設計パターン） |
| Testcontainers for .NET | 3.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、`PostgreSqlContainer` モジュールによる簡潔なセットアップ | MIT | GA（アクティブ開発中） |
| WebApplicationFactory | 10.0 | HttpHandler / エンドポイントテスト | ASP.NET Core + Giraffe エンドポイントのインメモリテスト、Giraffe.ViewEngine のレンダリング検証 | MIT | GA（.NET 10 に同梱） |
| ArchUnitNET | 0.11.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証。F# のファイル順コンパイルによる制約と併せて二重の防御を構成する | Apache 2.0 | GA（アクティブ開発中） |
| WireMock.Net | 1.6.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ | Apache 2.0 | GA（アクティブ開発中） |
| Microsoft.Playwright | 1.4x | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため（.NET 版バインディング） | Apache 2.0 | GA（アクティブ開発中） |
| coverlet + ReportGenerator | 6.x / 5.x | カバレッジ計測・レポート | `dotnet test` 統合によるカバレッジ収集（Cobertura 形式）、HTML レポート生成、SonarQube 連携 | MIT / Apache 2.0 | GA（アクティブ開発中） |

> **ArchUnitNET 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（`Domain` モジュールが `Infrastructure` モジュールを参照しない）
> 2. ドメイン層にフレームワーク依存を持ち込まないこと（Giraffe・Donald 等の技術的関心事）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port（関数レコード）経由で参照する）
> 4. 異なる Bounded Context 間で型を直接参照しないこと（ACL / Event 経由のみ）
>
> F# はプロジェクト内のファイル順コンパイルにより前方参照が禁止されるため、
> レイヤー順（Domain → Application → Infrastructure → Interfaces）にファイルを並べること自体が依存方向の静的な保証になる。
> ArchUnitNET はプロジェクト間・コンテキスト間の制約を補完的に検証する。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| dotnet CLI / MSBuild | 10.0.x | ビルドツール | ソリューション（`CargoTracker.sln`）単位のビルド・テスト・発行、`Directory.Build.props` による共通設定管理 | MIT | GA（.NET 10 に同梱） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保（SonarScanner for .NET） | LGPL 3.0 | GA（Community Edition） |
| Fantomas | 7.x | F# コードフォーマッタ | F# コミュニティ標準のフォーマッタ、`.editorconfig` によるスタイル設定、CI での `--check` 検証 | Apache 2.0 | GA（アクティブ開発中） |
| FSharpLint | 0.24.x | F# 静的解析（Lint） | 命名規約・冗長なコード・ヒントベースのリファクタリング提案、CI での自動チェック | MIT | GA（コミュニティ維持） |

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
| JetBrains Rider / VS Code + Ionide | - | IDE | F# 開発の標準環境。Rider は F# プラグイン統合、Ionide は F# コミュニティ標準の VS Code 拡張（型シグネチャ表示・FSI 統合） | Commercial / MIT | GA（JetBrains / コミュニティサポート） |
| F# Interactive (FSI) | 10.0 | REPL・スクリプティング | ドメイン関数の対話的検証、`.fsx` スクリプトによる探索的開発とプロトタイピング | MIT | GA（.NET 10 に同梱） |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。
ACL ポートはすべて F# の関数レコードとして定義し、実装は `IHttpClientFactory` から取得した `HttpClient` を部分適用で閉じ込める。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | IHttpClientFactory + HttpClient / WireMock.Net（テスト） | `NotificationPort` |

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- .NET: LTS バージョン（.NET 10）を採用し、次期 LTS（.NET 12）への移行はエコシステム対応を確認して計画する
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- ASP.NET Core / F#: .NET のメジャーバージョンに追従し、パッチバージョンは積極的に更新する。Giraffe / Donald / DbUp / Npgsql / FsToolkit.ErrorHandling はマイナーバージョンを定期的に更新する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| .NET / F# | 10.0 (LTS) / 9 | 12.0（次期 LTS） / 次期 F# | 2028 年 | ランタイム設定、NuGet パッケージ互換性 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Giraffe | 7.x | 次期メジャー | メジャー変更時 | HttpHandler DSL・ViewEngine API の互換性確認 |
| Donald / DbUp | 10.x / 6.x | 次期メジャー | メジャー変更時 | マッピング API・スクリプト適用処理の互換性確認 |
| FsToolkit.ErrorHandling | 4.x | 5.x | メジャー変更時 | CE（`asyncResult` / `validation`）の API 互換性確認 |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を F# の関数型パラダイムで自然に実現できる技術を優先した。
   判別共用体による Command / Query / Event の定義と Result 型による ROP ワークフローが CQRS を最小の依存で実現し、
   Donald による DTO への直接射影クエリは Read Model 最適化に適合する。MediatR のようなメディエータは
   関数の部分適用による合成ルートで代替し、依存を明示的かつコンパイル時検証可能にした。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポート（関数レコード）として抽象化し、
   IHttpClientFactory と WireMock.Net の組み合わせで実装・テストを完結できる構成とした。

3. **テスト容易性**: Port が関数レコードであるためモックライブラリなしでスタブを差し込め、
   ドメインが純粋関数であるため FsCheck によるプロパティベーステストが適用できる。
   さらに F# のファイル順コンパイルと ArchUnitNET の二重防御で、
   ヘキサゴナルアーキテクチャの依存関係制約がコードベースに継続的に適用されることを保証する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。インフラ・CI/CD・ドキュメント基盤は C# 版設計を踏襲し、
   言語変更の影響をアプリケーション層に閉じ込めた。
