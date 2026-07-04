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
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 2 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 3 件作成済み |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、ASP.NET Core 10、6 コンテキスト |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Razor SSR + htmx、Bootstrap 5、画面遷移設計 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義、ER 図、Dapper マッピング |
| [ドメインモデル設計](./design/domain-model.md) | 境界づけられたコンテキスト、集約・値オブジェクト・ドメインイベント |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計 |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、xUnit/Testcontainers、カバレッジ目標 |
| [非機能要件](./design/non_functional.md) | 性能・可用性・セキュリティ・保守性・拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視、バックアップ、デプロイ、障害対応 |
| [技術スタック選定](./design/tech_stack.md) | .NET 10 ベースの全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260704.md) | 設計ドキュメント一式のマルチパースペクティブレビュー結果（高 8 件・中 16 件・低 6 件） |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-0001 集約永続化戦略](./adr/0001-集約永続化戦略.md) | Dapper による集約の保存・再構築規約（全削除→再挿入・楽観的ロック） |
| [ADR-0002 UnitOfWork と post-commit イベントディスパッチ](./adr/0002-UnitOfWorkとpost-commitイベントディスパッチ.md) | トランザクション境界とドメインイベント発行タイミングの設計 |
| [ADR-0003 開発 SQLite / 本番 PostgreSQL の二方言運用](./adr/0003-開発SQLite本番PostgreSQLの二方言運用.md) | 二方言構成の条件付き採用と方言混入の抑止策 |

## 補足

- `development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
