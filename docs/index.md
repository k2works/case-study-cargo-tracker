# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [ADR](./adr/index.md) - 重要な技術的意思決定を記録します。
- [レビュー](./review/index.md) - 分析・開発成果物のレビュー結果を記録します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - 学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、UI、テスト、非機能、運用、技術スタック | 10 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 2 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 2 件作成済み |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | `index.md` を整備済み |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | Axon Framework 5 + Spring Boot による DDD・ヘキサゴナル・CQRS・Event Sourcing・Saga のマイクロサービス設計 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | React SPA・React Query・Zustand・Tailwind による CQRS バックエンド対応の SPA 設計 |
| [インフラストラクチャアーキテクチャ](./design/architecture_infrastructure.md) | Docker Compose（ローカル） / AWS ECS + Axon Server 2024.x LTS による実行環境・デプロイ設計 |
| [ドメインモデル設計](./design/domain-model.md) | 7 コンテキストの集約・値オブジェクト・ドメインイベント・コマンド・Saga（Axon 5 対応、過去レビュー指摘事項 28 件反映） |
| [データモデル設計](./design/data-model.md) | Database per Service の概念・物理データモデル、Event Store / Read Model / Auth DB の ER 図とテーブル定義 |
| [UI 設計](./design/ui_design.md) | OOUX に基づく画面一覧（27 画面）・画面遷移図・salt 画面イメージ・インタラクション設計 |
| [技術スタック](./design/tech_stack.md) | 全採用技術の一覧、バージョン・サポート期限・実在性確認チェックリスト・代替案 |
| [テスト戦略](./design/test_strategy.md) | ハイブリッドピラミッド型（ユニット + Axon Test + 統合 + E2E）、TDD/BDD 運用 |
| [非機能要件](./design/non_functional.md) | ISO/IEC 25010 ベースでフェーズ別 SLA（99.9% / 99.95%）、RPO 1h、セキュリティ、使用性等を数値定義 |
| [運用要件](./design/operation.md) | 運用体制・運用フロー・監視・バックアップ/リストア・障害対応・変更管理・運用 KPI |

### ADR

| ADR | 決定内容 |
| :--- | :--- |
| [ADR-0001](./adr/0001-axon-framework-adoption.md) | メッセージング基盤として Axon Framework 5 を採用する（フェーズ別稼働率・EE 移行計画を含む） |
| [ADR-0002](./adr/0002-mybatis-adoption.md) | データアクセスとして MyBatis を採用する |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | 参考プロジェクトのドメインモデル分析に対するマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [分析成果物レビュー](./review/分析成果物_review_20260512.md) | ADR 2 件 + 設計 10 件に対する XP 5 エージェント並列レビュー結果（高 24 件・中 15 件・低 4 件、Phase 1 着手済み） |

## 補足

- `strategy/`、`requirements/`、`design/`、`adr/`、`review/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
