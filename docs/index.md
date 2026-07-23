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
| [開発](./development/index.md) | リリース計画、開発戦略、IT1〜IT5 計画・ふりかえり・完了報告書、進捗管理 | IT5 完了（Phase 2 完了・Release 1.0 MVP 完成・残 31 SP・累計 68%） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 1 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 5 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 6 件作成済み |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、axum + cargo workspace、8 コンテキスト |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Askama SSR + htmx、Bootstrap 5 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker（静的バイナリ + distroless）、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義、ER 図、sqlx マッピング |
| [ドメインモデル設計](./design/domain-model.md) | 8 コンテキスト、集約・値オブジェクト・ドメインイベント（状態機械の正典） |
| [UI 設計](./design/ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計（24 画面） |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、cargo test / mockall / testcontainers-rs / proptest |
| [非機能要件](./design/non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視、バックアップ、デプロイ、障害対応 |
| [技術スタック選定](./design/tech_stack.md) | Rust / axum / sqlx / Askama を中心とした全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [Rust 版設計ドキュメントレビュー](./review/design_review_20260706.md) | 設計 10 ドキュメントのマルチパースペクティブレビュー結果（高 10 件・中 12 件・低 5 件、高・中対応済） |
| [IT1 開発成果物レビュー](./review/it1_development_review_20260718.md) | IT1 実装（9 クレート・約 4,400 行）のマルチパースペクティブレビュー結果（高 3 件・中 7 件・低 6 件） |
| [IT2 開発成果物レビュー](./review/it2_development_review_20260722.md) | IT2 実装（航海スケジュール・約 2,300 行）のマルチパースペクティブレビュー結果（高 6 件・中 6 件・低 5 件、高対応済） |
| [IT3 開発成果物レビュー](./review/it3_development_review_20260722.md) | IT3 実装（経路算出・選択・約 1,700 行）のマルチパースペクティブレビュー結果（高 3 件・中 5 件・低 4 件、高対応済） |
| [IT4 開発成果物レビュー](./review/it4_development_review_20260722.md) | IT4 実装（経路連携・予約確定・約 1,800 行）のマルチパースペクティブレビュー結果（高 4 件・中 5 件・低 4 件、高対応済） |
| [IT5 開発成果物レビュー](./review/it5_development_review_20260723.md) | IT5 実装（追跡・荷役・約 4,180 行）のマルチパースペクティブレビュー結果（高 4 件・中 4 件・低 3 件、高対応済） |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [0001: CQRS Read Model 配置](./adr/0001-cqrs-read-model-placement.md) | Read Model の sqlx 実装は infra-persistence に配置、app 層はクエリポート trait のみ |
| [0002: 認証方式（tower-sessions + 自前 RBAC）](./adr/0002-authentication-with-tower-sessions.md) | IT1 は axum-login ではなく tower-sessions + 自前 RBAC を採用（意図的逸脱） |
| [0003: DIP を composition root で回復](./adr/0003-dependency-injection-composition-root.md) | interface 層の依存を composition root（AppState に出力ポート trait）で注入（IT2） |
| [0004: BC 跨ぎ書き込みの一貫性](./adr/0004-cross-context-write-consistency.md) | BC 跨ぎ書き込みは単一トランザクションで束ねず各 BC 内整合＋冪等リトライで収束（IT4） |
| [0005: 予約状態機械の遷移ルール](./adr/0005-booking-status-state-machine.md) | 予約状態遷移を Cargo 集約に閉じ込め不正遷移を Result::Err で拒否（IT4） |
| [0006: 追跡状態導出と BC 跨ぎ回復戦略](./adr/0006-tracking-status-derivation-and-cross-context-recovery.md) | 追跡状態を保持イベント列から純粋関数で導出し Booking→Tracking の回復戦略を明文化（IT5） |

### 開発ジャーナル

| ドキュメント | 概要 |
| :--- | :--- |
| [2026-07-06](./journal/20260706.md) | セッション記録 |
| [2026-07-18](./journal/20260718.md) | セッション記録 |
| [2026-07-22](./journal/20260722.md) | IT2〜IT4 を 1 日で消化・予約状態機械実装・イテレーション運用スキル新設 |

## 補足

- `strategy/`、`requirements/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は各セッションの判断と学びを残す作業ログです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
