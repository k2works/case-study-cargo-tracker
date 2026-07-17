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
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | リリース計画・IT1〜IT5 計画/ふりかえり/完了報告を作成済み。IT6 計画を作成済み |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 6 件作成済み |
| [ジャーナル](./journal/) | 日次の開発ジャーナル（判断の経緯の記録） | 4 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 10 件作成済み |
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
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS、F# + Giraffe、7 コンテキスト |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Giraffe.ViewEngine SSR + htmx、Bootstrap 5 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD |
| [データモデル設計](./design/data-model.md) | 概念・論理データモデル、テーブル定義、ER 図、Donald マッピング |
| [ドメインモデル設計](./design/domain-model.md) | 境界づけられたコンテキスト、判別共用体による集約・値オブジェクト |
| [UI 設計](./design/ui_design.md) | 画面一覧・遷移図・ワイヤーフレーム・Giraffe.ViewEngine コンポーネント |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型テスト、xUnit/FsUnit/FsCheck/Testcontainers |
| [非機能要件](./design/non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性（ISO/IEC 25010 準拠） |
| [運用要件](./design/operation.md) | 監視、バックアップ、デプロイ、障害対応 |
| [技術スタック選定](./design/tech_stack.md) | F# 9 / .NET 10 ベースの全技術スタック一覧 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [F# 版設計ドキュメントレビュー](./review/設計ドキュメント_review_20260706.md) | 設計ドキュメント全 10 件のマルチパースペクティブレビュー結果（高 14 件・中 18 件・低 6 件、全件対応済み） |
| [開発成果物レビュー IT2](./review/開発成果物_IT2_review_20260715.md) | Booking Context（US04/05/06）実装・テスト・ADR-0007/0008（高 9 件・中 8 件・低 8 件） |
| [開発成果物レビュー IT3](./review/開発成果物_IT3_review_20260715.md) | Routing Context（US24/25/07/08）実装・テスト・ADR-0009（高 6 件・中 7 件・低 8 件） |
| [開発成果物レビュー IT4](./review/開発成果物_IT4_review_20260715.md) | 経路確定〜予約確定（US09-13）実装・テスト・ADR-0010（高 3 件・中 8 件・低 6 件） |
| [開発成果物レビュー IT5](./review/開発成果物_IT5_review_20260716.md) | 追跡・荷役（US14-18）実装・テスト・BC 間イベント連携（高 6 件・中 6 件・低 2 件） |

### ADR

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-0001](./adr/0001-モジュール構成は垂直スライスを採用.md) | モジュール構成は垂直スライス（`CargoTracker.<Context>.<Layer>`）を採用 |
| [ADR-0002](./adr/0002-ドメインイベントはPayloadレコード方式とpost-commitディスパッチを採用.md) | ドメインイベントは Payload レコード方式 + post-commit ディスパッチを採用 |
| [ADR-0003](./adr/0003-DBマイグレーションはDbUpによるforward-only方式を採用.md) | DB マイグレーションは DbUp による forward-only 方式を採用 |
| [ADR-0004](./adr/0004-Donaldによる集約永続化パターンを採用.md) | Donald による集約永続化パターンを採用 |
| [ADR-0005](./adr/0005-Cookie認証とuser_rolesによるRBACを採用.md) | Cookie 認証と user_roles による RBAC を採用 |
| [ADR-0006](./adr/0006-時刻とGUIDの注入ポートを採用.md) | 時刻と GUID の注入ポートを採用 |
| [ADR-0007](./adr/0007-経路設計中状態はBookingState_DU拡張で表現.md) | 経路設計中状態は BookingState DU 拡張で表現 |
| [ADR-0008](./adr/0008-荷主参照はShipperId永続化により業務識別子で行う.md) | 荷主参照は ShipperId 永続化により業務識別子で行う |
| [ADR-0009](./adr/0009-経路候補算出はRouting自コンテキストで構成する.md) | 経路候補算出は Routing 自コンテキストで構成する |
| [ADR-0010](./adr/0010-経路確定のRouting_Booking連携は合成層のACL変換で行う.md) | 経路確定の Routing→Booking 連携は合成層の ACL 変換で行う |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は日次の開発ジャーナル（判断の経緯の記録）を格納しています。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
