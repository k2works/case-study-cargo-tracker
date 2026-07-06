---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。Rust / axum / sqlx / PostgreSQL を中心にバックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-07-06T00:00:00.000Z
tags: design, tech-stack, rust, axum, sqlx, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（Askama SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。
Rust の型システムと cargo workspace による依存制約のコンパイル時強制を最大限活用する方針である。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Rust | stable (edition 2024) | アプリケーション実装言語 | 所有権・enum・newtype による不変条件の型レベル表現、GC なしの予測可能な性能、静的バイナリによる小型コンテナイメージ | MIT / Apache 2.0 | 安定版（6 週間サイクルでリリース） |
| tokio | 1.x | 非同期ランタイム | axum / sqlx / reqwest の共通基盤、broadcast チャネルによる in-process イベントバス実装 | MIT | GA（アクティブ開発中） |
| axum | 0.8.x | Web フレームワーク | Tower ミドルウェアエコシステムとの統合、型安全な extractor、tokio チームによるメンテナンス | MIT | GA（アクティブ開発中） |
| tower / tower-http | 0.5.x / 0.6.x | ミドルウェア | セッション・認証・トレーシング・CORS 等を Service 抽象で合成、ヘキサゴナルの横断的関心事を分離 | MIT | GA（アクティブ開発中） |
| sqlx | 0.8.x | データアクセス | `query_as!` マクロによる SQL のコンパイル時検証、ORM を介さない SQL 明示管理が CQRS Read Model 最適化に適合 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| sqlx migrate | 0.8.x（sqlx 同梱） | DB マイグレーション | バージョン管理されたスキーマ変更、CLI / 組み込み両対応、コンテキスト別ディレクトリ管理 | MIT / Apache 2.0 | GA（sqlx に同梱） |
| tower-sessions | 0.14.x | セッション管理 | Cookie ベースセッション、PostgreSQL ストア対応、Tower Layer として合成可能 | MIT | GA（アクティブ開発中） |
| axum-login | 0.17.x | 認証・認可 | フォームベース認証、`AuthzBackend` による RBAC（SALES / HANDLER 等のロール）、tower-sessions 統合 | MIT | GA（アクティブ開発中） |
| serde / serde_json | 1.x | シリアライズ | Rust 標準のシリアライズ基盤、DTO の derive による型安全な JSON 変換 | MIT / Apache 2.0 | GA（デファクトスタンダード） |
| thiserror | 2.x | エラー型定義 | レイヤ毎の型付きエラーを derive で定義。anyhow は使わず、ドメインエラーを網羅的に enum で表現する方針に適合 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| utoipa | 5.x | API ドキュメント（OpenAPI + Swagger UI） | derive マクロによる axum ハンドラ・DTO からの OpenAPI 生成、utoipa-swagger-ui による UI 提供 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| reqwest | 0.12.x | 外部連携 HTTP クライアント | 外部システム連携（ACL ポート trait の背後に隠蔽）、tokio 統合、コネクションプール内蔵 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| async-trait | 0.1.x | 非同期 trait ポート定義 | リポジトリ・EventPublisher 等の出力ポート trait を async fn で定義（dyn 互換が必要な箇所で使用） | MIT / Apache 2.0 | GA（安定版） |
| tracing / tracing-subscriber | 0.1.x / 0.3.x | 構造化ログ・トレーシング | tower-http との統合、リクエスト単位の span による観測性確保 | MIT | GA（アクティブ開発中） |

> **バージョン採用方針**: Rust は stable チャネル最新を追従し、edition 2024 を使用する。
> クレートのメジャーバージョンアップ（axum 0.8 → 0.9 等）は Cargo.toml の workspace dependencies で一元管理し、
> 影響範囲を確認のうえ随時追従する。判断の経緯は `docs/adr/` に記録すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Askama | 0.14.x | テンプレートエンジン（SSR） | Jinja2 風構文をコンパイル時に Rust コードへ変換し、テンプレートの変数参照ミスをビルドエラーで検出。SSR によるシンプルな構成、SEO 対応 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化。Askama の部分テンプレートと相性が良い | BSD 2-Clause | GA（アクティブ開発中） |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証、sqlx のコンパイル時検証対象 | PostgreSQL License | GA（EOL: 2028-11） |
| sqlx migrate | 0.8.x | DB マイグレーション | バージョン管理されたスキーマ変更、`sqlx::migrate!` によるアプリ起動時適用、テストコンテナへの適用も同一コード | MIT / Apache 2.0 | GA（sqlx に同梱） |

> **テスト環境の DB 設定**: テストでは testcontainers-rs により実 PostgreSQL コンテナを起動する。
> sqlx は SQL をコンパイル時に PostgreSQL スキーマと照合して検証するため、H2 のような代替インメモリ DB は不要である。
> 本番と同一の DB エンジンでテストすることで方言差異による不具合を排除する。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| cargo test | -（Rust 同梱） | テストフレームワーク | 言語標準のテストランナー、単体・統合・doc テストを統一的に実行 | MIT / Apache 2.0 | GA（Rust に同梱） |
| mockall | 0.13.x | モックライブラリ | `#[automock]` によるポート trait の自動モック生成、Application Service の単体テストに使用 | MIT / Apache 2.0 | GA（アクティブ開発中） |
| pretty_assertions | 1.x | アサーション差分表示 | 集約・値オブジェクトの比較失敗時にカラー差分を表示し、テストコードの可読性・デバッグ効率を向上 | MIT / Apache 2.0 | GA（安定版） |
| testcontainers-rs | 0.23.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した sqlx リポジトリ・Read Model の統合テスト | MIT / Apache 2.0 | GA（アクティブ開発中） |
| tower::ServiceExt | -（tower 同梱） | Handler テスト | `oneshot` による axum Router の HTTP レベルテスト（サーバ起動不要） | MIT | GA（tower に同梱） |
| wiremock | 0.6.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ（Rust ネイティブ、async 対応） | MIT / Apache 2.0 | GA（アクティブ開発中） |
| Playwright | 1.5x | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |
| cargo-llvm-cov | 0.6.x | カバレッジ計測 | LLVM ソースベースカバレッジによる正確な計測、lcov / HTML 出力で CI・SonarQube 連携 | MIT / Apache 2.0 | GA（アクティブ開発中） |

> **アーキテクチャ検証（ArchUnit の代替）**: 依存関係ルールはテストではなく **cargo workspace のクレート分割**で強制する。
>
> 1. ドメイン層がインフラ層に依存しないこと → `domain-*` クレートの Cargo.toml に axum / sqlx / tokio / reqwest を宣言しない
> 2. ドメイン層に Web フレームワークの型を持ち込まないこと → 依存宣言がなければ import 自体がコンパイルエラー
> 3. アプリケーション層がインフラ層を直接参照しないこと → `app-*` は `domain-*` のポート trait のみに依存
> 4. 異なる Bounded Context 間でクラスを直接参照しないこと → コンテキスト間の依存を Cargo.toml に宣言しない（ACL / Event 経由のみ）
>
> Cargo.toml の依存宣言そのものが構造検証であり、違反は `cargo build` で即座に検出される。
> 補助的に cargo-deny の `bans` 設定で禁止依存を明示的に宣言する。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| cargo (workspace) | -（Rust 同梱） | ビルドツール・依存管理 | workspace members によるクレート分割でヘキサゴナル境界・Bounded Context 境界をコンパイラで強制 | MIT / Apache 2.0 | GA（Rust に同梱） |
| clippy | -（Rust 同梱） | Lint | `-D warnings` で警告をエラー化し CI でブロック、イディオマティックな Rust を強制 | MIT / Apache 2.0 | GA（Rust に同梱） |
| rustfmt | -（Rust 同梱） | コード整形 | 標準フォーマッタによるスタイル統一、レビューの機械的論点を排除 | MIT / Apache 2.0 | GA（Rust に同梱） |
| cargo-audit | 0.21.x | 依存脆弱性監査 | RustSec Advisory DB に基づく既知脆弱性の検出、CI で毎ビルド実行 | MIT / Apache 2.0 | GA（RustSec プロジェクト） |
| cargo-deny | 0.16.x | 依存ポリシー検査 | ライセンス検査・重複依存検出・禁止クレート宣言（アーキテクチャ制約の補助的強制） | MIT / Apache 2.0 | GA（Embark Studios） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測（cargo-llvm-cov の lcov 取り込み）・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 27.x | コンテナ化 | マルチステージビルド + distroless / scratch ベースイメージ。Rust の静的バイナリにより数十 MB 級の極小イメージと低メモリフットプリントを実現（JVM 比で起動時間・メモリ効率に大きな利点） | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（アプリ + PostgreSQL + SonarQube）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減。Rust の低メモリ消費により小さいタスクサイズで運用可能 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・API キーの安全な管理（起動時に環境変数へ注入） | - | GA（AWS マネージド） |
| AWS CloudWatch | - | 監視・ログ | tracing の JSON 出力を取り込み、メトリクス・アラートを統合管理 | - | GA（AWS マネージド） |
| AWS Route 53 | - | DNS | ドメイン管理、ヘルスチェックフェイルオーバー | - | GA（AWS マネージド） |
| AWS ACM | - | TLS 証明書 | HTTPS 証明書の自動更新 | - | GA（AWS マネージド） |

## ドキュメント

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs | 1.x | ドキュメントサイト生成 | Markdown ベース、Material テーマ、PlantUML 統合 | BSD 2-Clause | GA（アクティブ開発中） |
| PlantUML | - | ダイアグラム生成 | UML 図・ER 図・ワイヤーフレームのコードベース管理、テキストから図を生成 | GPL 3.0 | GA（アクティブ開発中） |
| Mermaid | 11.x | ダイアグラム生成 | Markdown 内インライン図表、MkDocs 統合 | MIT | GA（アクティブ開発中） |
| rustdoc | -（Rust 同梱） | API リファレンス生成 | doc コメントからのドキュメント自動生成、doc テストによるサンプルコードの動作保証 | MIT / Apache 2.0 | GA（Rust に同梱） |

## 開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RustRover / VS Code + rust-analyzer | - | IDE / エディタ | Rust 開発の標準環境、型情報に基づくリファクタリング支援 | Commercial / MIT | GA（JetBrains / rust-analyzer チーム） |
| rust-analyzer | -（最新） | 言語サーバー | 補完・型ヒント・マクロ展開表示、sqlx マクロのオフラインモード対応 | MIT / Apache 2.0 | GA（Rust 公式プロジェクト） |
| cargo-watch | 8.x | ホットリロード開発 | ファイル変更検知による自動再ビルド・再起動 | Apache 2.0 | GA（安定版） |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、Playwright 実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。
いずれもドメイン層に定義した ACL ポート trait の背後に reqwest クライアント実装を隠蔽し、テストでは wiremock でスタブする。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名（trait） |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | reqwest / wiremock（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | reqwest / wiremock（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| reqwest / wiremock（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | reqwest / wiremock（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | reqwest / wiremock（テスト） | `NotificationPort` |

## バージョン管理方針

### 安定版優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Rust: stable チャネル最新を追従する（edition は 2024 で固定し、次 edition 移行時に ADR を起票する）
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- クレート: workspace dependencies で一元管理し、パッチ・マイナーは Dependabot / cargo-audit の結果を確認して随時追従する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Rust edition | 2024 | 次期 edition | リリース後に評価 | `cargo fix --edition` による機械的移行が中心 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高）、sqlx オフラインキャッシュ再生成 |
| axum | 0.8.x | 0.9.x 以降 | メジャー変更時 | extractor / ミドルウェア API の変更確認 |
| sqlx | 0.8.x | 0.9.x 以降 | メジャー変更時 | マクロ・マイグレーション API の変更確認 |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Rust の型システムと cargo workspace で自然に実現できる技術を優先した。
   特に sqlx による SQL の明示的管理とコンパイル時検証は CQRS の Read Model 最適化に適合し、
   クレート分割による依存制約の強制は ArchUnit のような事後検証を不要にする。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポート trait として抽象化し、reqwest と wiremock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: mockall によるポート trait のモック化と testcontainers-rs による実 PostgreSQL テストを組み合わせ、
   単体テストの高速性と統合テストの忠実性を両立する。不変条件の多くは型で表現されるため、テストすべき状態空間自体が縮小する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。Rust の静的バイナリによる distroless / scratch イメージは
   攻撃面の縮小・イメージ小型化・起動高速化・メモリ効率の向上に寄与する。
