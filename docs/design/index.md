# 設計

設計ドキュメントです。要件定義に基づき、バックエンド・フロントエンド・インフラのアーキテクチャから、データモデル、ドメインモデル、UI、テスト戦略、非機能要件、運用要件まで包括的に設計しています。

## ドキュメント一覧

### アーキテクチャ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [バックエンドアーキテクチャ](architecture_backend.md) | DDD + ヘキサゴナル + CQRS、Spring Boot 4 / Java 25、6 コンテキスト + 共有カーネル | 作成済 |
| [フロントエンドアーキテクチャ](architecture_frontend.md) | Thymeleaf SSR + htmx、Bootstrap 5、画面遷移設計 | 作成済 |
| [インフラストラクチャ](architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD | 作成済 |

### モデル設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [データモデル設計](data-model.md) | 概念・論理データモデル、テーブル定義（18 テーブル）、ER 図 | 作成済 |
| [ドメインモデル設計](domain-model.md) | 6 コンテキスト + 共有カーネル、集約・エンティティ・値オブジェクト・ドメインイベント | 作成済 |

### UI/UX設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [UI 設計](ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計（25 画面） | 作成済 |

### 品質・運用

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [テスト戦略](test_strategy.md) | ピラミッド型テスト、ツール選定、カバレッジ目標、Testcontainers / Playwright | 作成済 |
| [非機能要件](non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性を整理（ISO/IEC 25010 準拠） | 作成済 |
| [運用要件](operation.md) | 監視、バックアップ、デプロイ、障害対応を整理 | 作成済 |

### その他

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [技術スタック選定](tech_stack.md) | バックエンド・フロントエンド・インフラ・テスト全技術スタック一覧 | 作成済 |

## 正典（Single Source of Truth）

同じ事柄を複数のドキュメントで定義しないため、項目ごとに正典を定める。他ドキュメントは値を再掲せず参照する。

| 項目 | 正典 |
| :--- | :--- |
| US 採番 | `docs/requirements/user_story.md`（US01〜US27） |
| リリーススコープ・MVP | [`docs/development/release_scope.md`](../development/release_scope.md) |
| 技術的意思決定 | [`docs/adr/`](../adr/index.md) |
| RBAC ロール定義 | [non_functional.md](non_functional.md) §4.1 |
| SLA / SLO・RTO / RPO | [non_functional.md](non_functional.md) §3 |
| 監視アラート閾値 | [non_functional.md](non_functional.md) §5.2 |
| ログ保持期間 | [non_functional.md](non_functional.md) §4.4 |
| BC 間 ACL ポート一覧 | [domain-model.md](domain-model.md) |
| テストツール・カバレッジゲート | [test_strategy.md](test_strategy.md) |
| 画面一覧・enum 日本語ラベル | [ui_design.md](ui_design.md) |

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
- 2026-08-06 のマルチパースペクティブレビュー結果は [`docs/review/設計ドキュメント_review_20260806.md`](../review/設計ドキュメント_review_20260806.md) を参照してください。
