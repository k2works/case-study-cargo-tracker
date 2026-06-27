# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [ジャーナル](./journal/index.md) - セッションでの判断と学びの物語です。
- [記事](./article/index.md) - 学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能、運用、技術スタック | 10 件作成済み (Haskell 版) |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | リリース計画 + IT1 計画 + 分析完了報告書 |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 4 段階セットアップ手順書を配置 |
| [ジャーナル](./journal/index.md) | セッションでの判断・学びの物語形式の記録 | 1 件 (2026-06-26) |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 3 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 2 件作成済み (ADR 0001, 0002) |
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

### 設計ドキュメント (Haskell 版)

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS を Servant + Warp + ReaderT で実装 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Lucid SSR + htmx + Bootstrap 5 |
| [インフラアーキテクチャ](./design/architecture_infrastructure.md) | AWS ECS Fargate + RDS PostgreSQL + Terraform |
| [ドメインモデル設計](./design/domain-model.md) | 8 境界付けられたコンテキスト・7 集約の戦術的設計 |
| [データモデル設計](./design/data-model.md) | 20 テーブル・postgresql-simple マッピング規約 |
| [UI 設計](./design/ui_design.md) | 24 画面・OOUX・Lucid ビュー構成 |
| [技術スタック](./design/tech_stack.md) | GHC 9.10 + Stack + hspec + hedgehog 等の選定 |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型 (Domain 95% / 全体 85%) |
| [非機能要件](./design/non_functional.md) | ISO/IEC 25010 準拠 SLA / SLO / セキュリティ |
| [運用要件](./design/operation.md) | 監視・バックアップ・障害対応・変更管理 |

### ADR ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR 0001](./adr/0001-haskell-servant-stack.md) | Haskell 版バックエンドスタックとして Servant + Warp を採用 |

### 開発計画ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](./development/release_plan.md) | 25 US / 73 SP・4 フェーズ × 8 IT (0.1 → 0.2 → 1.0 MVP → 2.0 GA) |
| [分析フェーズ完了報告書](./development/analysis_completion_report.md) | 全 16 ドキュメントの成果サマリ・主要決定事項・残課題 |
| [IT1 計画](./development/iteration_plan-1.md) | AUTH / US02 / US03 / US04 / US24 の 20 SP イテレーション計画 |
| [IT1 完了報告書](./development/iteration_report-1.md) | 成功基準 6 件中 5 OK / 1 未達 (HPC 未計測)、主要メトリクス・学び |
| [IT1 ふりかえり (KPT)](./development/retrospective-1.md) | Keep 13・Problem 14・Try 18 (必達 10 + 推奨 8) |
| [IT2 計画](./development/iteration_plan-2.md) | US01 / US04+US05 / US06 / US25 の 10 SP + Try 必達 8 SP、Release 0.1 Internal Alpha 計画 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー (高 11・中 12・低 5) |
| [分析整合性セルフレビュー](./review/analysis_consistency_review_20260626.md) | grep ベース機械的整合性検証 (集約↔テーブル、UC↔US 等) |
| [分析多視点レビュー](./review/analysis_multi_perspective_review_20260626.md) | XP 5 エージェント並列レビュー (高 16・中 16・低 10) |
| [IT1 コードレビュー](./review/it1_code_review_20260626.md) | IT1 全体の XP 5 エージェント並列レビュー (高 10・中 13・低 8) |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
