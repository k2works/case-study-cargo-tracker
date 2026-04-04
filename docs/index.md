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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | IT7 完了・Phase 3 進行中（74/82SP）・IT8 計画済み |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 14 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 6 件作成済み |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 28 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート | 17 件のテンプレートを配置 |

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
| [ユーザーストーリー](./requirements/user_story.md) | US 24 件（Phase 1〜3）・受け入れ基準・トレーサビリティマトリックス |

### 設計ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、Spring Boot 4、6 コンテキスト |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Thymeleaf SSR + htmx、Bootstrap 5、画面遷移設計 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義（16 テーブル）、ER 図 |
| [ドメインモデル設計](./design/domain-model.md) | 6 コンテキスト、集約・エンティティ・値オブジェクト・ドメインイベント |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計（17 画面） |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、ツール選定、カバレッジ目標、WireMock 契約テスト |
| [非機能要件](./design/non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性を整理（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視、バックアップ、デプロイ、障害対応を整理 |
| [技術スタック選定](./design/tech_stack.md) | バックエンド・フロントエンド・インフラ・テスト全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260331.md) | 設計ドキュメント全体のマルチパースペクティブレビュー結果（高 20 件・中 9 件・低 3 件） |
| [ADR・非機能・運用レビュー](./review/ADR_非機能_運用_review_20260331.md) | ADR 4 件・非機能要件・運用要件のマルチパースペクティブレビュー結果（高 9 件・中 9 件・低 5 件） |
| [US04 コードレビュー](./review/US04_review_20260331.md) | IT1 US04 貨物予約登録機能の実装コードレビュー |
| [IT2 US01 開発レビュー](./review/IT2_US01_review_20260401.md) | IT2 US01「輸送見積を作成する」の 7 エージェント並列レビュー結果（高 11 件・中 6 件・低 5 件） |
| [IT2 実装レビュー](./review/IT2_implementation_review_20260402.md) | IT2（輸送見積・ルート検索）全体実装の 5 エージェント並列レビュー結果（高 9 件・中 11 件・低 7 件） |
| [IT3 実装レビュー](./review/it3_review_20260402.md) | IT3（特殊貨物予約・予約確定・追跡）の 5 エージェント並列レビュー結果 |
| [IT3 UI/UX レビュー](./review/it3_uiux_review_20260402.md) | IT3（特殊貨物予約・予約確定・追跡）の UI/UX マルチパースペクティブレビュー結果 |
| [IT4 実装レビュー](./review/it4_review_20260402.md) | IT4 US10（handling BC 全レイヤー・荷役作業一覧・ナビゲーション）の実装レビュー結果 |
| [IT4 UI/UX レビュー](./review/it4_uiux_review_20260402.md) | IT4 US10（荷役作業登録フォーム・一覧画面）の UI/UX マルチパースペクティブレビュー結果 |
| [IT4 US11〜US13 実装・UI/UX レビュー](./review/it4_us11_us13_review_20260402.md) | IT4 US11（引取記録）・US12（手動更新）・US13（追跡照会）の実装・UI/UX マルチパースペクティブレビュー結果 |
| [IT4 第 2 回コードレビュー](./review/it4_review2_20260403.md) | IT4 第 2 回コードレビュー（例外 BC・追跡画面 US11〜US13 レビュー対応後）の結果 |
| [IT5 UI/UX レビュー](./review/it5_uiux_review_20260403.md) | IT5 US14（例外事象記録）の UI/UX マルチパースペクティブレビュー結果（高 5 件・中 4 件・低 2 件） |
| [IT5 分析成果物レビュー](./review/it5_analysis_review_20260403.md) | IT5（US14/US15/US16）ユーザーストーリー・ドメインモデル・データモデル・UI 設計の 5 エージェント並列レビュー結果（高 9 件・中 13 件・低 6 件） |
| [IT7 実装レビュー](./review/it7_review_20260404.md) | IT7（US19/US20/US21 経路候補算出）全体実装の 5 エージェント並列レビュー結果（高 7 件・中 11 件・低 6 件） |
| [IT7 UI/UX レビュー](./review/it7_uiux_review_20260404.md) | IT7（routing/search.html・design-condition.html）の UI/UX マルチパースペクティブレビュー結果（高 4 件・中 6 件・低 3 件） |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-001](./adr/001-java-springboot-version-strategy.md) | Java 25 LTS / Spring Boot 4.0 採用と移行ロードマップ |
| [ADR-002](./adr/002-transactional-event-listener.md) | ドメインイベントに @TransactionalEventListener(AFTER_COMMIT) を使用する |
| [ADR-003](./adr/003-discount-policy-as-entity.md) | DiscountPolicy をエンティティとして設計し、Phase 2 以降に昇格保留 |
| [ADR-004](./adr/004-shipper-self-service-out-of-scope.md) | 荷主セルフサービス機能を Phase 1 スコープ外とする |
| [ADR-005](./adr/005-windows-docker-desktop-testcontainers.md) | Windows Docker Desktop では Testcontainers を `docker_engine_linux` に接続する |
| [ADR-006](./adr/006-enable-h2-console-on-spring-boot-4.md) | Spring Boot 4 の開発環境では H2 Console 専用モジュールと security 例外を明示設定する |

### 運用ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [アプリ開発環境セットアップ](./operation/dev_app_instrunction.md) | ローカルアプリケーション開発環境の構築手順（Java 25 / Spring Boot 4 / Gradle 9） |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
