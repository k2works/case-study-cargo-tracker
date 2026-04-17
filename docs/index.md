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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | 23 件作成済み（IT1〜IT7 完了） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 4 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 10 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 12 件作成済み |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 28 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート | 16 件のテンプレートを配置 |

### 戦略ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ビジネスアーキテクチャ](./strategy/business_architecture.md) | ビジネスモデル・バリューストリーム・ケイパビリティ・ビジネスシナリオ |
| [インセプションデッキ](./strategy/inception-deck.md) | プロジェクトの目的・スコープ・リスク・ロードマップ（10 の問い） |

### 要件定義ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [要件定義書](./requirements/requirements_definition.md) | RDRA 2.0 に基づく 4 層（システム価値・外部環境・境界・内部構造） |
| [ビジネスユースケース](./requirements/business_usecase.md) | 業務レベル BUC 13 件・アクター目的リスト |
| [システムユースケース](./requirements/system_usecase.md) | システム境界 UC 12 件（完全形式） |
| [ユーザーストーリー](./requirements/user_story.md) | US 18 件・受け入れ基準・トレーサビリティマトリックス |

### 設計ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、Spring Boot 4、6 コンテキスト |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Thymeleaf SSR + htmx、Bootstrap 5、画面遷移設計 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義（16 テーブル）、ER 図 |
| [ドメインモデル設計](./design/domain-model.md) | 6 コンテキスト、集約・エンティティ・値オブジェクト・ドメインイベント |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計（17 画面） |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、ツール選定、カバレッジ目標 |
| [非機能要件](./design/non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視、バックアップ、デプロイ、障害対応 |
| [技術スタック選定](./design/tech_stack.md) | バックエンド・フロントエンド・インフラ・テスト全技術スタック一覧 |

### 開発ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](./development/release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略 |
| [IT1 計画](./development/iteration_plan-1.md) | イテレーション 1 の詳細計画（US02・US03・US04） |
| [IT1 ふりかえり](./development/retrospective-1.md) | IT1 の KPT 分析と IT2 への申し送り事項 |
| [IT1 完了報告書](./development/iteration_report-1.md) | IT1 の実績メトリクス・成果物一覧・アクションアイテム |
| [IT2 計画](./development/iteration_plan-2.md) | イテレーション 2 の詳細計画（US05・US13 + IT1 技術的負債） |
| [IT2 ふりかえり](./development/retrospective-2.md) | IT2 の KPT 分析と IT3 への申し送り事項 |
| [IT2 完了報告書](./development/iteration_report-2.md) | IT2 の実績メトリクス・成果物一覧・アクションアイテム |
| [IT3 計画](./development/iteration_plan-3.md) | イテレーション 3 の詳細計画（IT2改善・US01・US06） |
| [IT3 ふりかえり](./development/retrospective-3.md) | IT3 の KPT 分析と IT4 への申し送り事項 |
| [IT3 完了報告書](./development/iteration_report-3.md) | IT3 の実績メトリクス・成果物一覧・アクションアイテム |
| [IT4 計画](./development/iteration_plan-4.md) | イテレーション 4 の詳細計画（IT3改善・US07・US08） |
| [IT4 ふりかえり](./development/retrospective-4.md) | IT4 の KPT 分析と IT5 への申し送り事項 |
| [IT4 完了報告書](./development/iteration_report-4.md) | IT4 の実績メトリクス・成果物一覧・アクションアイテム |
| [IT5 計画](./development/iteration_plan-5.md) | イテレーション 5 の詳細計画（IT4改善・US09・US10・US11） |
| [IT5 ふりかえり](./development/retrospective-5.md) | IT5 の KPT 分析と IT6 への申し送り事項 |
| [IT5 完了報告書](./development/iteration_report-5.md) | IT5 の実績メトリクス・成果物一覧・アクションアイテム |
| [IT6 計画](./development/iteration_plan-6.md) | イテレーション 6 の詳細計画（IT5改善・US22・US23） |
| [IT6 ふりかえり](./development/retrospective-6.md) | IT6 の KPT 分析とプロジェクト全体の振り返り |
| [IT6 完了報告書](./development/iteration_report-6.md) | IT6 の実績メトリクス・成果物一覧（Java 272 件・E2E 67 件） |
| [リリース完了報告書 v1.0.0](./development/release_report-1_0_0.md) | Release 1.0 完了報告書（6 IT・155 コミット・339 テスト） |
| [IT7 計画](./development/iteration_plan-7.md) | イテレーション 7 の詳細計画（IT6-改善・US14・US15） |
| [IT7 ふりかえり](./development/retrospective-7.md) | IT7 の KPT 分析（追跡番号発行・荷役作業記録・IT7 申し送り事項） |
| [IT7 完了報告書](./development/iteration_report-7.md) | IT7 の実績メトリクス・成果物一覧（E2E 78 件・カバレッジ 81.7%） |

### 運用ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [アプリケーション開発環境セットアップ](./operation/dev_app_instrunction.md) | ローカルアプリケーション開発環境の構築手順 |
| [Playwright E2E テスト](./operation/dev_e2e_instruction.md) | Playwright による E2E テスト環境の構築手順 |
| [API E2E テスト](./operation/dev_e2e_api_instruction.md) | MockMvc + Testcontainers による API E2E テストの構築手順 |
| [開発環境セットアップ](./operation/dev_infa_instruction.md) | Heroku コンテナを使った開発環境の構築手順 |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-001](./adr/001-spring-boot-4-java-25.md) | Spring Boot 4.0.5 + Java 25 を採用する |
| [ADR-002](./adr/002-gradle-groovy-dsl.md) | ビルドツールに Gradle 9.x (Groovy DSL) を採用する |
| [ADR-003](./adr/003-spotbugs-ignore-failures-java25.md) | SpotBugs を ignoreFailures=true で運用する（Java 25 対応） |
| [ADR-004](./adr/004-swagger-ui-conditional.md) | Swagger UI を環境変数で条件付き有効化する |
| [ADR-005](./adr/005-husky-lint-staged-precommit.md) | Husky + lint-staged で pre-commit 品質チェックを実施する |
| [ADR-006](./adr/006-testcontainers-singleton-pattern.md) | Testcontainers でシングルトンコンテナパターンを採用する |
| [ADR-007](./adr/007-playwright-e2e-pom-pattern.md) | Playwright E2E テストで Page Object Model パターンを採用する |
| [ADR-008](./adr/008-sonarqube-local-quality-gate.md) | ローカル SonarQube で品質ゲートを管理する |
| [ADR-009](./adr/009-github-actions-ci-pipeline.md) | GitHub Actions で Build & Test + E2E の 2 ジョブ CI を構成する |
| [ADR-010](./adr/010-practical-ddd-package-structure.md) | Practical DDD in Enterprise Java のパッケージ構成を採用する |
| [ADR-011](./adr/011-archunit-hexagonal-rules.md) | ArchUnit でヘキサゴナルアーキテクチャの依存関係ルールを自動検証する |
| [ADR-012](./adr/012-default-profile-login-prefill.md) | デフォルトプロファイルでログインフォームに認証情報をプリセットする |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [IT1 実装成果物レビュー](./review/IT1_review_20260404.md) | IT1 荷主登録・貨物予約登録のコードレビュー結果（高 8 件・中 8 件・低 3 件） |
| [IT1 UI/UX レビュー](./review/IT1_uiux_review_20260404.md) | IT1 Thymeleaf テンプレートの UI/UX レビュー結果（高 8 件・中 8 件・低 5 件） |
| [IT2 US13 実装成果物レビュー](./review/US13_review_20260406.md) | US13 予約確定・キャンセルの実装レビュー結果 |
| [IT2 予約管理画面 UI/UX レビュー](./review/booking_uiux_review_20260406.md) | IT2 予約管理 Thymeleaf テンプレートの UI/UX レビュー結果 |
| [ファクトリメソッドリファクタリング コードレビュー](./review/factory_method_review_20260406.md) | Shipper ファクトリメソッド・RouteSpecification のリファクタリングレビュー（高 1 件・中 2 件・低 2 件） |
| [IT3 実装成果物レビュー](./review/IT3_review_20260407.md) | IT3 US01・US06・Estimation コンテキストのコードレビュー結果（高 7 件・中 9 件・低 6 件） |
| [IT3 UI/UX レビュー](./review/IT3_uiux_review_20260407.md) | IT3 Estimation コンテキスト Thymeleaf テンプレートの UI/UX レビュー結果（高 5 件・中 7 件・低 3 件） |
| [IT5 実装成果物レビュー](./review/IT5_review_20260409.md) | IT5 US09・US10・US11 経路割り当て機能のコードレビュー結果（高 9 件・中 7 件・低 4 件） |
| [IT5 UI/UX レビュー](./review/IT5_uiux_review_20260409.md) | IT5 US09・US10・US11 経路割り当て画面（route.html・show.html・navbar.html）の UI/UX レビュー結果（高 7 件・中 7 件・低 3 件） |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
