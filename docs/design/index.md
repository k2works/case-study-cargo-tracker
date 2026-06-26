# 設計

設計ドキュメントです。要件定義に基づき、バックエンド・フロントエンド・インフラのアーキテクチャから、データモデル、ドメインモデル、UI、テスト戦略、非機能要件、運用要件まで包括的に設計しています。

## ドキュメント一覧

### アーキテクチャ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [バックエンドアーキテクチャ](architecture_backend.md) | DDD・ヘキサゴナル・CQRS を Servant / Haskell で実装 | 作成済 |
| [フロントエンドアーキテクチャ](architecture_frontend.md) | Lucid SSR + htmx による画面構成と状態管理 | 作成済 |
| [インフラストラクチャ](architecture_infrastructure.md) | AWS ECS Fargate + RDS + Terraform 構成 | 作成済 |

### モデル設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [データモデル設計](data-model.md) | 概念・論理データモデル、テーブル定義、postgresql-simple マッピング | 作成済 |
| [ドメインモデル設計](domain-model.md) | 8 境界付けられたコンテキストの集約・値オブジェクト・ドメインサービスを Haskell で定義 | 作成済 |

### UI/UX設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [UI 設計](ui_design.md) | 画面一覧 (24 画面)、画面遷移図、ワイヤーフレーム、Lucid ビュー構成 | 作成済 |

### 品質・運用

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [テスト戦略](test_strategy.md) | hspec/hedgehog/testcontainers-hs/hspec-wai/Playwright のピラミッド戦略 | 作成済 |
| [非機能要件](non_functional.md) | ISO/IEC 25010 準拠の性能・可用性・セキュリティ・保守性・拡張性・ユーザビリティ目標 | 作成済 |
| [運用要件](operation.md) | 運用フロー・監視 (CloudWatch)・バックアップ/リカバリ・障害対応・変更管理・キャパシティ管理 | 作成済 |

### その他

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [技術スタック選定](tech_stack.md) | Haskell + Servant 構成の全技術一覧とバージョン管理方針 | 作成済 |

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
