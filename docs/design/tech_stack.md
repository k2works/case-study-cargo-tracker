---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: design, tech-stack, typescript, nestjs, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（TSX SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Node.js | 24.18 LTS | アプリケーション実行環境 | 長期サポート（Active LTS）、豊富なエコシステム、TypeScript との親和性 | MIT | GA（LTS: Node.js 24、Maintenance 期は 2028-04 まで） |
| TypeScript | 5.x | アプリケーション実装言語 | 静的型付けによる保守性、DDD の型による設計表現、NestJS との親和性 | Apache 2.0 | GA（アクティブ開発中） |
| NestJS | 11.x | アプリケーションフレームワーク | DI コンテナ・モジュールシステムによる開発効率、DDD 実装との親和性、Spring に類似した構造 | MIT | GA（11.x リリース済み） |
| @nestjs/common / @nestjs/core | 11.x | コアフレームワーク | DI・ライフサイクル管理・例外フィルタの基盤、ヘキサゴナルの依存性逆転を支援 | MIT | GA（NestJS に同梱） |
| NestJS Controller | 11.x | Web フレームワーク | TSX View Controller・REST Controller の統合、ヘキサゴナルの Primary Adapter として機能 | MIT | GA（NestJS に同梱） |
| NestJS Guard + Passport | 11.x / 0.7.x | 認証・認可 | セッションベース認証、RBAC（ROLE_SALES / ROLE_HANDLER 等）、CSRF 保護、ガードによる認可 | MIT | GA（@nestjs/passport 同梱） |
| Kysely | 0.27.x | データアクセス | 型安全な SQL ビルダーによる SQL の明示的管理、CQRS の Read Model クエリ最適化との親和性 | MIT | GA（アクティブ開発中） |
| @nestjs/swagger | 11.x | API ドキュメント（Swagger UI） | REST API の自動ドキュメント生成、デコレータベースのスキーマ定義、環境別有効化 | MIT | GA（NestJS 11 対応済み） |
| @nestjs/event-emitter | 3.x | ドメインイベント発行 | `EventEmitter2` による同一プロセス内イベント配信、疎結合なコンテキスト間通信 | MIT | GA（NestJS に対応） |
| TSX + htmx | - / 2.x | テンプレートへの権限連携 | 型付き props（user/roles）による型安全なロール別 UI 制御、htmx 属性での部分更新 | MIT / BSD 2-Clause | GA（安定版） |

> **バージョン採用方針**: Node.js 24.18 LTS / NestJS 11 は安定版であるため、そのまま採用する。
> TypeScript は 5.x の最新安定版に追従し、破壊的変更を伴うメジャー更新時のみ ADR に影響範囲を記録する。
> エコシステム（Kysely・Passport 等）は SemVer に従い、メジャー更新は ADR に移行ロードマップを記録すること。
> 詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| TSX（React 19 / react-dom/server） | 19.x | テンプレートエンジン（SSR） | renderToStaticMarkup による SSR、テンプレートも TypeScript の型検査対象となり画面とドメイン型の整合をコンパイル時に保証、SEO 対応（クライアント React・ハイドレーションは不使用） | MIT | GA（安定版） |
| Bootstrap | 5.3.3 | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.4 | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |
| Vite | 6.x | フロントエンドアセットバンドル | 静的アセット（CSS/JS）のバンドル・開発サーバー、HMR による開発効率 | MIT | GA（アクティブ開発中） |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番用 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| Testcontainers（PostgreSQL） | 10.x | テスト用実 DB コンテナ | 本番と同一の PostgreSQL でテストを実行、Kysely の SQL 互換性を実 DB で担保 | MIT | GA（アクティブ開発中） |
| pg-mem | 3.x | ローカル開発用インメモリ DB | PostgreSQL 互換のインメモリ DB。ローカル開発のデフォルト DB として Docker 不要の高速起動を実現。SQL 互換性検証は Testcontainers を正とする | MIT | GA（アクティブ開発中） |
| node-pg-migrate | 7.x | DB マイグレーション | バージョン管理されたスキーマ変更、TypeScript 統合、コンテキスト別マイグレーション管理 | MIT | GA（アクティブ開発中） |

> **テスト環境の DB 設定**: 基本方針として Testcontainers による実 PostgreSQL 16 を使用する。
> ローカル開発は pg-mem（インメモリ）をデフォルトとし、Docker 不要で即起動できる開発体験を優先する。統合テスト・CI では Testcontainers（実 PostgreSQL）を正とし、SQL 互換性の乖離を検出する。実 PostgreSQL でのローカル検証が必要な場合は Docker Compose 構成を併用する。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Vitest | 3.x | テストフレームワーク・モック・アサーション | テスト実行・モック（`vi.fn`）・アサーション（`expect`）を統合、TypeScript ネイティブ、パラメータ化テスト対応 | MIT | GA（アクティブ開発中） |
| Testcontainers | 10.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した統合テスト、Kysely マッパーの検証 | MIT | GA |
| supertest | 7.x | Controller テスト | NestJS エンドポイントの入出力テスト、TSX テンプレートのレンダリング検証 | MIT | GA（アクティブ開発中） |
| dependency-cruiser | 16.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（ドメイン層がインフラ層に依存しないこと等） | MIT | GA（アクティブ開発中） |
| nock | 14.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システム HTTP スタブ | MIT | GA（アクティブ開発中） |
| Playwright | 1.44+ | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |

> **dependency-cruiser 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（`domain` ディレクトリが `infrastructure` ディレクトリを import しない）
> 2. ドメイン層に NestJS デコレータを使用しないこと（`@Injectable`, `@Controller`, `@Module` 等を import しない）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port 経由で参照する）
> 4. 異なる Bounded Context 間でモジュールを直接参照しないこと（ACL/Event 経由のみ）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| npm scripts | - | タスクランナー | ビルド・テスト・Lint の統合実行、追加ツール不要 | - | GA（Node.js に同梱） |
| tsc / tsx | 5.x / 4.x | 型チェック・スクリプト実行 | `tsc` で型検査、`tsx` で TypeScript を直接実行（開発・スクリプト） | Apache 2.0 / MIT | GA（アクティブ開発中） |
| esbuild | 0.24.x | 本番ビルド | 高速なバンドル・トランスパイル、本番イメージ最小化 | MIT | GA（アクティブ開発中） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| ESLint + typescript-eslint | 9.x / 8.x | TypeScript コードスタイル・静的解析 | コーディング規約の自動チェック、型情報を用いたバグパターン検出 | MIT | GA（アクティブ開発中） |
| Prettier | 3.x | コードフォーマッター | コードフォーマットの統一、レビューコストの削減 | MIT | GA（アクティブ開発中） |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | 環境の再現性、開発・本番環境の一貫性、マルチステージビルドによる本番イメージ最小化 | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル検証環境構築（オプション） | 実 PostgreSQL・SonarQube を用いた検証用マルチコンテナ管理（ローカル開発のデフォルトは pg-mem） | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・API キーの安全な管理、環境変数への注入 | - | GA（AWS マネージド） |
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
| VS Code / WebStorm | - | IDE | TypeScript / NestJS 開発の標準 IDE、DDD パターン対応リファクタリング支援 | MIT / Commercial | GA（アクティブ開発中 / JetBrains サポート） |
| Node.js | 24.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | fetch / undici / nock（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | fetch / undici / nock（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| fetch / undici / nock（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | fetch / undici / nock（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | fetch / undici / nock（テスト） | `NotificationPort` |

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Node.js: Active LTS バージョン（Node.js 24.18）を採用し、次期 LTS リリース後は安定を確認して移行する
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- NestJS: 11.x のマイナーバージョンは積極的に追従する（メジャー更新は ADR で移行計画を管理）
- TypeScript: 5.x の最新安定版に随時追従する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Node.js | 24.18 LTS | 24 LTS | 2027 年 | ランタイム設定、ライブラリ互換性 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| NestJS | 11.x | 12.x（次メジャー） | メジャー変更時 | DI・デコレータ API の変更確認 |
| node-pg-migrate | 7.x | 8.x | メジャー変更時 | マイグレーションスクリプト |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Node.js / NestJS エコシステムで自然に実現できる技術を優先した。
   特に Kysely による型安全な SQL の明示的管理は CQRS の Read Model 最適化に適合する。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポートとして抽象化し、fetch / undici と nock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: dependency-cruiser によるアーキテクチャルールの自動検証を追加し、ヘキサゴナルアーキテクチャの依存関係制約が
   コードベースに継続的に適用されることを保証する。Vitest でテスト・モック・アサーションを統合し、TypeScript ネイティブな高速テストを実現する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。
