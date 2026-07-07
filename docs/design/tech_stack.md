---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-07-07T00:00:00.000Z
tags: design, tech-stack, ruby, rails, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムで採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（ERB SSR + Hotwire）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Ruby | 3.4.x | アプリケーション実装言語 | YJIT による性能向上、簡潔な文法による DDD の表現力、Rails との親和性 | BSD 2-Clause | GA（安定版、EOL はリリースから約 3 年） |
| Ruby on Rails | 8.0.x | アプリケーションフレームワーク | フルスタック構成による開発効率、Hotwire・Solid Cache/Queue 同梱、規約による一貫性 | MIT | GA（アクティブ開発中） |
| Puma | 6.x | アプリケーションサーバ | Rails 標準、マルチスレッド対応、ECS コンテナとの親和性 | BSD 3-Clause | GA（Rails に同梱） |
| Active Record | 8.0.x | データアクセス（Write Model） | 集約の永続化をリポジトリ層で分離しつつ、マイグレーション・トランザクション管理を標準機能で実現 | MIT | GA（Rails に同梱） |
| Query Object + 生 SQL | - | データアクセス（Read Model） | CQRS の Read Model クエリ最適化。`select_all` / Arel による明示的な SQL 管理 | MIT | GA（Rails に同梱） |
| Rails 標準認証 | 8.0.x | 認証 | `has_secure_password` + セッション認証。Rails 8 認証ジェネレータによる自前実装で依存を最小化 | MIT | GA（Rails に同梱） |
| Pundit | 2.x | 認可（RBAC） | ロール別 Policy（sales / handler 等）によるシンプルな認可制御、テスト容易性 | MIT | GA（アクティブ開発中） |
| ActiveSupport::Notifications | - | ドメインイベント発行 | Spring Events 相当の疎結合なコンテキスト間通信を標準ライブラリで実現（DomainEvents モジュールでラップ） | MIT | GA（Rails に同梱） |
| Faraday | 2.x | 外部 API クライアント | ACL ポートの Secondary Adapter 実装、ミドルウェアによるリトライ・タイムアウト制御 | MIT | GA（アクティブ開発中） |
| Packwerk | 3.x | コンテキスト境界管理 | Bounded Context / レイヤ間の依存関係を静的検証（ArchUnit 相当） | MIT | GA（Shopify メンテナンス） |

> **バージョン採用方針**: Ruby 3.4 / Rails 8.0 を基準とする。Rails のマイナーバージョンは積極的に追従し、
> メジャーバージョンアップは GA 後にエコシステム（gem 互換性）の成熟を確認してから移行する。
> 詳細は `docs/adr/` を参照すること。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ERB | - | テンプレートエンジン（SSR） | Rails 標準、サーバーサイドレンダリングによるシンプルな構成、SEO 対応 | MIT | GA（Rails に同梱） |
| Hotwire（Turbo） | 8.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォーム部分更新を実現、JS 最小化 | MIT | GA（Rails に同梱） |
| Hotwire（Stimulus） | 3.x | 軽量 JS コントローラ | フォーム補助・動的振る舞いの最小限の JavaScript 実装 | MIT | GA（Rails に同梱） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| Propshaft + importmap-rails | - | アセット管理 | ビルドレスなアセット配信、Node.js 依存の排除 | MIT | GA（Rails に同梱） |

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 全環境共通 RDBMS | 信頼性・ACID 準拠・JSON 型サポート・運用実績、DDD 集約のトランザクション整合性を保証 | PostgreSQL License | GA（EOL: 2028-11） |
| Active Record マイグレーション | - | DB マイグレーション | バージョン管理されたスキーマ変更、Rails 標準、`schema.rb` による現行スキーマの可視化 | MIT | GA（Rails に同梱） |

> **テスト環境の DB 設定**: 開発・テスト・本番のすべてで PostgreSQL 16 を使用する（Docker Compose で提供）。
> 環境間の RDBMS 差異による不具合を排除できるため、H2 のような代替インメモリ DB は使用しない。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RSpec | 7.x（rspec-rails） | テストフレームワーク | Ruby / Rails 標準的 BDD フレームワーク、ドメイン仕様の表現力 | MIT | GA（アクティブ開発中） |
| FactoryBot | 6.x | テストデータ生成 | 集約・エンティティのテストデータを宣言的に管理 | MIT | GA（アクティブ開発中） |
| Capybara + capybara-playwright-driver | 3.x / 0.5+ | E2E テスト（system spec） | Turbo の動的更新・ポーリングを含む画面の E2E テストに適しているため | MIT | GA（アクティブ開発中） |
| WebMock | 3.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ | MIT | GA（アクティブ開発中） |
| SimpleCov | 0.22+ | カバレッジ計測 | カバレッジ目標の計測、CI での品質ゲート連携 | MIT | GA（アクティブ開発中） |
| Packwerk | 3.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャ・Bounded Context の依存関係ルール自動検証 | MIT | GA（Shopify メンテナンス） |

> **Packwerk 最低限の検証ルール**:
>
> 1. ドメイン層がインフラ層に依存しないこと（domain パック が infrastructure パックを参照しない）
> 2. ドメイン層に Rails（Active Record 等）への依存を持ち込まないこと（PORO で実装する）
> 3. アプリケーション層がインフラ層を直接参照しないこと（Port 経由で参照する）
> 4. 異なる Bounded Context 間でクラスを直接参照しないこと（ACL/Event 経由のみ）
>
> **注**: Packwerk が静的検証できるのはパック間参照（1・3・4）のみです。ルール 2（特定定数への依存禁止）は
> Packwerk の守備範囲外のため、RuboCop カスタム cop（domain ディレクトリ内での `ActiveRecord` /
> `ApplicationRecord` 参照禁止）で担保します。詳細は ADR 0001 を参照してください。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Bundler | 2.x | 依存関係管理 | Gemfile / Gemfile.lock による再現性のある依存管理 | MIT | GA（Ruby に同梱） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、ワークフロー定義の柔軟性、OIDC 認証による AWS デプロイ | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |
| RuboCop（+ rubocop-rails, rubocop-rspec） | 1.x | コードスタイルチェック・静的解析 | コーディング規約の自動チェック、Rails / RSpec 固有ルールの適用 | MIT | GA（アクティブ開発中） |
| Brakeman | 7.x | セキュリティ静的解析 | Rails 固有の脆弱性パターン（SQL インジェクション・XSS 等）の自動検出 | Brakeman Public Use License | GA（アクティブ開発中） |
| bundler-audit | 0.9+ | 依存脆弱性チェック | gem の既知脆弱性の自動検出 | GPL 3.0 | GA（アクティブ開発中） |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | 環境の再現性、開発・本番環境の一貫性、マルチステージビルドによる本番イメージ最小化 | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（アプリ + PostgreSQL + SonarQube）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・ヘルスチェック（Rails `/up`）・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・`RAILS_MASTER_KEY` の安全な管理 | - | GA（AWS マネージド） |
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
| RubyMine / VS Code | - | IDE | Ruby / Rails 開発支援、リファクタリング・デバッグ支援 | Commercial / MIT | GA |
| Node.js | 22.x | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | GA（LTS） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA（アクティブ開発中） |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | Faraday / WebMock（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | Faraday / WebMock（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS）| Faraday / WebMock（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | Faraday / WebMock（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | Faraday / WebMock（テスト） | `NotificationPort` |

## バージョン管理方針

### 安定版優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Ruby: 安定版（3.4.x）を採用し、次期安定版へはリリース後 6 か月以内に移行する
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- Rails: 8.x のマイナーバージョンは積極的に追従する（8.0 → 8.1 → 8.2）

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Ruby | 3.4.x | 3.5.x | 2027 年 | gem 互換性、YJIT 設定 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Rails | 8.0.x | 8.x 最新 | 随時 | デフォルト設定（`load_defaults`）の変更確認 |
| Bootstrap | 5.3.x | 6.x | メジャー変更時 | テンプレート・CSS クラス |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Rails のエコシステムで実現できる技術を優先した。
   ドメイン層は Active Record に依存しない PORO とし、Packwerk で境界を静的に検証する。
   Read Model は Query Object + 生 SQL で CQRS のクエリ最適化に適合させる。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポートとして抽象化し、Faraday と WebMock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: 開発・テスト・本番で同一の PostgreSQL を使用し、Packwerk によるアーキテクチャルールの自動検証で
   ヘキサゴナルアーキテクチャの依存関係制約がコードベースに継続的に適用されることを保証する。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら
   可用性要件（SLA 99.9%）を満たす構成とした。Rails 8 の Solid Cache / Solid Queue により
   Redis 等の追加ミドルウェアなしでキャッシュ・ジョブ実行を実現する。
