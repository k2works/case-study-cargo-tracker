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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | リリース計画 + IT1〜IT9 計画・ふりかえり・完了報告書 + Release 0.1 ゲート確認 + Release 1.0 MVP 完了報告書 + Release 2.0 GA ゲート確認 / 完了報告書 |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 5 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 13 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 19 件作成済み (0001-0010 + 0013-0021) |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS を Play Framework / Scala 3 で実装する設計 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Twirl SSR + htmx による画面構成・状態管理・画面遷移 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS Fargate + RDS 構成、sbt ベースの CI/CD、監視設計 |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、18 テーブル定義、ScalikeJDBC マッピング方針 |
| [ドメインモデル設計](./design/domain-model.md) | 8 コンテキストの集約・値オブジェクト・ドメインイベントを Scala 3 で定義 |
| [UI 設計](./design/ui_design.md) | OOUX に基づく 21 画面の一覧・遷移図・ワイヤーフレーム・インタラクション設計 |
| [技術スタック選定](./design/tech_stack.md) | Scala 3 / Play / ScalikeJDBC 等の技術一覧・選定理由・バージョン管理方針 |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト形状・テストレベル定義・カバレッジ目標・TDD ワークフロー |
| [非機能要件](./design/non_functional.md) | ISO/IEC 25010 に基づく性能・可用性・セキュリティ・保守性・拡張性の数値目標 |
| [運用要件](./design/operation.md) | 運用フロー・監視・バックアップ・障害対応・変更管理・キャパシティ管理 |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [0001 Play Framework 採用](./adr/0001-play-framework-scala-stack.md) | Scala 版バックエンドスタックの選定（Play 3.x + Twirl + ScalikeJDBC） |
| [0002 bcrypt + Play Session](./adr/0002-bcrypt-and-session-management.md) | 認証のパスワードハッシュとセッション管理 |
| [0003 PricingService 共有](./adr/0003-pricing-service-shared-between-estimate-and-billing.md) | 料金計算ドメインサービスを Estimation と Billing で共通化 |
| [0004 US26 を UC 横断扱い](./adr/0004-us26-as-cross-cutting-story.md) | 認証・認可ストーリーを UC 横断として扱う |
| [0005 経路探索アルゴリズム](./adr/0005-route-search-algorithm.md) | DFS + 深さ制限の経路探索（IT2 spike → IT3 US08 で再評価） |
| [0006 Voyage データモデル拡張](./adr/0006-voyage-data-model-extension.md) | US07 検索のための voyage 拡張・中間テーブル・Routing 用 RouteCandidate/RoutingLeg 分離 |
| [0007 楽観ロック Either API](./adr/0007-optimistic-lock-either-api.md) | 楽観ロックを `Either[DomainError.ConcurrentModification, A]` で表現する |
| [0008 queryservices 命名拡張](./adr/0008-queryservices-package-naming.md) | queryservices パッケージで `*Query` / `*Command` / `*Result` / `*Candidate` を許容 |
| [0009 経路選択集約](./adr/0009-route-candidate-selection-aggregate.md) | 経路選択を独立集約 `RouteCandidateSelection` として永続化する |
| [0010 追跡番号採番ポリシー](./adr/0010-tracking-number-policy.md) | TrackingNumber 採番ポリシー |
| [0013 tracking_number シーケンス採番](./adr/0013-tracking-number-sequence-numbering.md) | tracking_number 採番を PostgreSQL シーケンスに統一 |
| [0014 集約 Snapshot ADT](./adr/0014-aggregate-snapshot-adt.md) | 集約 Snapshot 値オブジェクトの ADT 化 |
| [0015 Billing Money 統一](./adr/0015-billing-money-shared-domain.md) | Billing Money を shared.domain に統一 |
| [0016 HandlingOrchestrator tx 境界](./adr/0016-handling-orchestrator-transaction-boundary.md) | HandlingOrchestrator の単一 DB.localTx 化 (案 A) |
| [0017 BookingPublicApi](./adr/0017-booking-public-api-port.md) | Booking Context 公開 Port パターン |
| [0018 MailNotificationPort](./adr/0018-mail-notification-port.md) | メール通知 Port + Logging Adapter |
| [0019 Payment 集約方針](./adr/0019-payment-aggregation-vs-invoice-status.md) | Invoice 集約内 paymentStatus 採用 (案 B) |
| [0020 公開追跡例外表示](./adr/0020-public-tracking-exception-display.md) | 公開追跡画面の例外表示方針 |
| [0021 Port パターン規約](./adr/0021-port-pattern-convention.md) | 公開 (application.api) / 入力 / 出力 Port の規約 + ArchUnit ルール 6 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260612.md) | Scala 版設計ドキュメント全 10 件のマルチパースペクティブレビュー結果（高 11 件・中 13 件・低 6 件） |
| [リリース計画レビュー](./review/release_plan_review_20260620.md) | Scala 版 take-1 のリリース計画レビュー結果（高 9 件・中 11 件・低 9 件） |
| [IT1 実装レビュー](./review/it1_implementation_review_20260620.md) | IT1 実装全体（73 ファイル 3,305 行）のマルチパースペクティブレビュー結果（高 8 件・中 19 件・低 7 件） |
| [IT2 実装レビュー](./review/it2_implementation_review_20260621.md) | IT2 実装全体（134 ファイル 5,034 行）のマルチパースペクティブレビュー結果（高 10 件・中 10 件・低 11 件） |
| [IT3 実装レビュー](./review/it3_implementation_review_20260621.md) | IT3 実装全体（Routing US07/US08 + ADR 0006 + SELECT 句修正）のマルチパースペクティブレビュー結果（高 8 件・中 17 件・低 9 件） |
| [IT4 セルフレビュー](./review/it4_self_review_20260621.md) | IT4 実装の自己レビュー結果（高 6 件 H1-H6）— IT5 申し送り |
| [IT5 セルフレビュー](./review/it5_self_review_20260622.md) | IT5 実装（US14/US15/US18 + Tracking/Handling Context 新設）の自己レビュー結果（高 7 件 H1-H7）— IT6 申し送り |
| [IT6 実装レビュー](./review/it6_implementation_review_20260623.md) | IT6 実装全体（US16/US17/US21 + Billing Context 新設）のマルチパースペクティブレビュー結果（高 8 件・中 10 件） |
| [IT7 実装レビュー](./review/it7_implementation_review_20260623.md) | IT7 実装全体（US19/US20 + ADR 0014/0015 + Flyway V18-V22）のマルチパースペクティブレビュー結果（高 12 件・中 17 件・低 15 件） |
| [IT8 セルフレビュー](./review/it8_self_review_20260624.md) | IT8 実装（US22/US23 + ADR 0016-0020 + Flyway V23/V26-V28）のセルフレビュー（高 3 件・中 3 件・低 3 件、総合 A） |
| [IT8 実装レビュー](./review/it8_implementation_review_20260624.md) | IT8 実装全体のマルチパースペクティブレビュー (XP 5 エージェント並列、高 8 件・中 5 件・低 3 件、総合 A) |
| [IT9 セルフレビュー](./review/it9_self_review_20260625.md) | IT9 実装（US27-30 + ADR 0021 + Flyway V29/V30）のセルフレビュー（高 4 件・中 5 件・低 3 件、総合 A） |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
