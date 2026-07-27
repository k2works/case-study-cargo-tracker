# 設計

設計ドキュメントです。要件定義に基づき、バックエンド・フロントエンド・インフラのアーキテクチャから、データモデル、ドメインモデル、UI、テスト戦略、非機能要件、運用要件まで包括的に設計しています。

## ドキュメント一覧

### アーキテクチャ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [バックエンドアーキテクチャ](architecture_backend.md) | DDD + ヘキサゴナル + CQRS、NestJS 11 / Node.js 24.18 LTS、7 コンテキスト + Shared Kernel | 作成済 |
| [フロントエンドアーキテクチャ](architecture_frontend.md) | TSX SSR + htmx、Bootstrap 5、画面遷移設計 | 作成済 |
| [インフラストラクチャ](architecture_infrastructure.md) | AWS ECS/RDS、Docker、GitHub Actions CI/CD | 作成済 |

### モデル設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [データモデル設計](data-model.md) | 概念・論理データモデル、テーブル定義（16 テーブル）、ER 図 | 作成済 |
| [ドメインモデル設計](domain-model.md) | 7 コンテキスト + Shared Kernel、集約・エンティティ・値オブジェクト・ドメインイベント | 作成済 |

### UI/UX設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [UI 設計](ui_design.md) | 画面一覧・画面遷移図・ワイヤーフレーム・インタラクション設計（17 画面） | 作成済 |

### 品質・運用

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [テスト戦略](test_strategy.md) | ピラミッド型テスト、Vitest / Testcontainers / Playwright、nock 契約テスト | 作成済 |
| [非機能要件](non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性を整理（ISO/IEC 25010 準拠） | 作成済 |
| [運用要件](operation.md) | 監視、バックアップ、デプロイ、障害対応を整理 | 作成済 |

### その他

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [技術スタック選定](tech_stack.md) | TypeScript / NestJS / Kysely / PostgreSQL ほか全技術スタック一覧 | 作成済 |

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
