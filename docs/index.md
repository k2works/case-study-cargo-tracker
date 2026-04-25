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
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | 10 件作成済み |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | `index.md` を整備済み |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 1 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 2 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 1 件作成済み |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 30 件のドキュメントを配置 |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD・ヘキサゴナル・CQRS をマイクロサービスとして Spring Boot 4.x で実装 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | React SPA（TanStack Query + Zustand + Tailwind CSS） |
| [インフラストラクチャアーキテクチャ](./design/architecture_infrastructure.md) | AWS ECS Fargate + RDS PostgreSQL + Amazon MQ |
| [データモデル](./design/data-model.md) | Database per Service パターンの ER 設計 |
| [ドメインモデル](./design/domain-model.md) | DDD 戦術的設計パターンに基づくドメインモデル |
| [UI 設計](./design/ui-design.md) | 画面遷移図・ワイヤーフレーム |
| [テスト戦略](./design/test_strategy.md) | テストピラミッド・カバレッジ目標 |
| [非機能要件](./design/non_functional.md) | 性能・セキュリティ・可用性 |
| [運用要件](./design/operation.md) | 監視・バックアップ・障害対応 |
| [技術スタック](./design/tech_stack.md) | Java 25 / Spring Boot 4.0.5 / React 19.x / Gradle 9.2.1 |

### 運用ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [アプリケーション開発環境セットアップ](./operation/アプリケーション開発環境セットアップ手順書.md) | ローカル開発環境の構築手順（バックエンド・フロントエンド・Docker Compose） |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [分析成果物レビュー](./review/分析成果物レビュー_review_20260424.md) | 分析成果物全体のマルチパースペクティブレビュー結果 |

### ADR ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-001: Heroku API ルーティングと CORS 設定](./adr/001-heroku-api-routing-and-cors.md) | Heroku 環境で発生した `404` / `403` を解消するための設定方針と実装判断 |

## 補足

- `strategy/`（2 件）、`requirements/`（4 件）、`design/`（10 件）は分析フェーズのドキュメントが揃っています。
- `operation/` はアプリケーション開発環境セットアップ手順書が作成済みです。開発環境・ステージング・本番の手順書は今後作成予定です。
- `development/` はリリース計画作成後にドキュメントが追加されます。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
