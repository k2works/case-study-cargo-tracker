# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - 学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | 10 件作成済み（Axon Kafka + Heroku + Aiven 構成） |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | 13 件作成済み（リリース計画・IT1-IT4 計画/ふりかえり/完了報告書） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 2 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 2 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 6 件作成済み |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 32 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート | 18 件のテンプレートを配置 |

### 戦略ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ビジネスアーキテクチャ](./strategy/business_architecture.md) | ビジネスモデル・バリューストリーム・ケイパビリティ・ビジネスシナリオ |
| [インセプションデッキ](./strategy/inception-deck.md) | プロジェクトの目的・スコープ・リスク・ロードマップ（10 の問い） |

### 要件定義ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [要件定義書](./requirements/requirements_definition.md) | RDRA 2.0 に基づく 4 層（システム価値・外部環境・境界・内部構造） |
| [ビジネスユースケース](./requirements/business_usecase.md) | 業務レベル BUC 21 件・アクター目的リスト |
| [システムユースケース](./requirements/system_usecase.md) | システム境界 UC 19 件（完全形式） |
| [ユーザーストーリー](./requirements/user_story.md) | US 25 件・受け入れ基準・トレーサビリティマトリックス |

### 設計ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | Axon Framework 5 + Axon Kafka Extension + Aiven 構成のバックエンド設計 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | React 19 + Vite + TypeScript フロントエンド設計 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | Heroku + Aiven Managed Kafka インフラ構成 |
| [データモデル設計](./design/data-model.md) | ER 図・テーブル定義 |
| [ドメインモデル設計](./design/domain-model.md) | エンティティ・値オブジェクト・集約 |
| [UI 設計](./design/ui_design.md) | 画面遷移図・画面イメージ |
| [テスト戦略](./design/test_strategy.md) | テストピラミッド・Testcontainers（Kafka）・カバレッジ目標 |
| [非機能要件](./design/non_functional.md) | 性能・セキュリティ・可用性・保守性要件 |
| [運用要件](./design/operation.md) | 運用フロー・監視設計・障害対応手順 |
| [技術スタック選定](./design/tech_stack.md) | Spring Boot 4 / Java 25 / Axon Kafka / Heroku の技術選定 |

### ADR ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-0001](./adr/0001-axon-kafka-aiven-adoption.md) | メッセージング基盤として Axon Kafka Extension + Aiven Managed Kafka を採用 |
| [ADR-0002](./adr/0002-mybatis-adoption.md) | データアクセスとして MyBatis を採用 |
| [ADR-0006](./adr/0006-heroku-deployment-setup.md) | Heroku Container Registry を用いた開発環境デプロイ構成 |
| [ADR-0007](./adr/0007-unify-db-initialization-with-flyway.md) | `local-h2` を含む DB 初期化を Flyway に統一し、`schema.sql` を廃止 |
| [ADR-0008](./adr/0008-pagination-strategy.md) | 一覧 API にページネーション (Offset/Limit + PageResponse) を採用 |
| [ADR-0009](./adr/0009-cross-service-event-saga.md) | cross-service イベント連携と Axon Saga を採用（Kafka tracking モード、提案中） |

### 運用ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [アプリケーション開発環境セットアップ手順書](./operation/アプリケーション開発環境セットアップ手順書.md) | ローカルアプリケーション開発環境の構築手順（Java 25 + Gradle + Kafka） |
| [開発環境セットアップ手順書](./operation/開発環境セットアップ手順書.md) | Heroku Container Registry デプロイ手順（Axon Kafka + Aiven） |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [IT2 ページネーション機能レビュー](./review/pagination_review_20260525.md) | IT2 荷主・予約一覧ページネーション機能のマルチパースペクティブレビュー結果（高 4 件・中 7 件・低 6 件） |

## 補足

- `development/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
