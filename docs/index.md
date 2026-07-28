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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | Phase 1・Phase 2 完了 + Phase 3 前半（IT1-IT5・67 SP・達成率 100%・Release 0.2.0） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 8 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | `index.md` を整備済み |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、Rails 8、Packwerk による 8 コンテキスト境界管理 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | ERB SSR + Hotwire（Turbo / Stimulus）、Bootstrap 5 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker、Puma、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義（19 テーブル）、ER 図 |
| [ドメインモデル設計](./design/domain-model.md) | 8 コンテキスト、集約・エンティティ・値オブジェクト・ドメインイベント |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム（17+ 画面） |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、RSpec / Capybara + Playwright / WebMock |
| [非機能要件](./design/non_functional.md) | 性能・可用性・セキュリティ・保守性・拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視・バックアップ・デプロイ・障害対応 |
| [技術スタック選定](./design/tech_stack.md) | Ruby 3.4 / Rails 8 を軸とした全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260707.md) | Rails 版設計ドキュメント全 10 件のマルチパースペクティブレビュー結果（高 11 件・中 13 件・低 6 件） |
| [IT1 実装レビュー](./review/IT1実装_review_20260728.md) | IT1 実装（認証・荷主登録・骨格）のマルチパースペクティブレビュー結果（高 6 件・中 8 件・低 6 件） |
| [IT2 実装レビュー](./review/IT2実装_review_20260728.md) | IT2 実装（貨物予約・Booking Context・ACL）のマルチパースペクティブレビュー結果（高 5 件・中 8 件・低 8 件） |
| [IT3 実装レビュー](./review/IT3実装_review_20260728.md) | IT3 実装（航海・Routing Context・Location・外部 ACL）のマルチパースペクティブレビュー結果（高 6 件・中 10 件・低 8 件） |
| [IT4 実装レビュー](./review/IT4実装_review_20260728.md) | IT4 実装（経路確定・再算出・予約確定・通知基盤 ADR-0002）のマルチパースペクティブレビュー結果（高 5 件・中 6 件・低 7 件） |
| [IT5 実装レビュー](./review/IT5実装_review_20260728.md) | IT5 実装（追跡番号発行・荷役・状態手動更新・Tracking/Handling Context）のマルチパースペクティブレビュー結果（高 5 件・中 7 件・低 8 件） |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
