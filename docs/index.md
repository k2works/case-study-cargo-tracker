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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | IT5 完了（累計 62SP、Phase3 10/21SP・中盤局面完了） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 1 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 7 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 9 件作成済み |
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
| [ビジネスユースケース](./requirements/business_usecase.md) | 業務レベル BUC 22 件・アクター目的リスト |
| [システムユースケース](./requirements/system_usecase.md) | システム境界 UC 20 件（完全形式） |
| [ユーザーストーリー](./requirements/user_story.md) | US 27 件・受け入れ基準・トレーサビリティマトリックス |

### 設計ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、NestJS 11 / Node.js 24.18 LTS、7 コンテキスト + Shared Kernel |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | TSX SSR + htmx、Bootstrap 5、コンポーネント設計・状態管理 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS Fargate / RDS PostgreSQL 16、Docker、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義、ER 図 |
| [ドメインモデル設計](./design/domain-model.md) | 7 コンテキスト + Shared Kernel、集約・値オブジェクト・ドメインイベント |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・ロール別到達性マトリクス |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、Vitest / Testcontainers / Playwright、nock 契約テスト |
| [非機能要件](./design/non_functional.md) | 性能・可用性・セキュリティ・保守性・拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視・バックアップ・デプロイ・障害対応 |
| [技術スタック選定](./design/tech_stack.md) | TypeScript / NestJS / Kysely / PostgreSQL ほか全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260727.md) | TypeScript 版設計一式のマルチパースペクティブレビュー結果（高 11 件・中 13 件・低 8 件、全件対応済み） |
| [IT1 実装レビュー](./review/IT1実装_review_20260728.md) | IT1（認証・荷主登録・スケルトン）のマルチパースペクティブレビュー結果（高 5 件・中 8 件・低 4 件、高優先度対応済み） |
| [IT2 実装レビュー](./review/IT2実装_review_20260728.md) | IT2（見積・貨物予約・引き渡し）のマルチパースペクティブレビュー結果（高 7 件・中 8 件、高優先度対応済み） |
| [IT3 実装レビュー](./review/IT3実装_review_20260729.md) | IT3（航海スケジュール・経路候補算出）のマルチパースペクティブレビュー結果（高 8 件・中 11 件、高優先度対応済み） |
| [IT4 実装レビュー](./review/IT4実装_review_20260729.md) | IT4（経路確定・荷主通知・予約確定・追跡番号発行）のマルチパースペクティブレビュー結果（クローズ内対応 8 件・次 IT 引き継ぎ 8 件） |
| [IT5 実装レビュー](./review/IT5実装_review_20260730.md) | IT5（荷役作業記録・引取・貨物状態手動更新）のマルチパースペクティブレビュー結果（クローズ内対応 12 件・次 IT 引き継ぎ 8 件） |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-001](./adr/001-nestjs-as-application-framework.md) | アプリケーションフレームワークとして NestJS を採用する |
| [ADR-002](./adr/002-kysely-and-node-pg-migrate.md) | データアクセスに Kysely、マイグレーションに node-pg-migrate を採用する |
| [ADR-003](./adr/003-tsx-ssr-with-htmx.md) | フロントエンドを TSX SSR + htmx で構成する |
| [ADR-004](./adr/004-pgmem-local-testcontainers-ci.md) | ローカル開発は pg-mem、テストの正は Testcontainers PostgreSQL とする |
| [ADR-005](./adr/005-event-emitter-context-integration.md) | コンテキスト間連携は NestJS EventEmitter による同一プロセス内イベントとする |
| [ADR-006](./adr/006-session-auth-without-passport.md) | 認証はセッションベースの自作ガードとし Passport を採用しない（実行は SWC/tsc） |
| [ADR-007](./adr/007-shared-kernel-and-stub-acl.md) | 共有カーネルに Location/CargoType を配置、Routing 候補算出は外部経路 ACL へ段階移行 |
| [ADR-008](./adr/008-routing-candidate-port-boundary.md) | 見積概算候補と経路候補 Port を分離、Booking の RouteCandidateAcl で経路紐付け、追跡番号は Booking 側で暫定採番 |
| [ADR-009](./adr/009-post-commit-side-effects.md) | コミット後副作用（通知・イベント）はコマンド失敗として扱わない |

## 補足

- `development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
