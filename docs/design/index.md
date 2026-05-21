# 設計

設計ドキュメントです。要件定義に基づき、バックエンド・フロントエンド・インフラのアーキテクチャから、データモデル、ドメインモデル、UI、テスト戦略、非機能要件、運用要件まで包括的に設計しています。

**構成方針（take-5）**: Axon Framework 5 + **Axon Kafka Extension**（イベントバス）+ **Aiven Managed Kafka** + **Heroku** デプロイ。take-4 の Axon Server 構成から変更。

## ドキュメント一覧

### アーキテクチャ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [バックエンドアーキテクチャ](architecture_backend.md) | DDD・ヘキサゴナル・CQRS・Event Sourcing・Saga + Axon Kafka Extension | 作成済み |
| [フロントエンドアーキテクチャ](architecture_frontend.md) | React SPA、状態管理、Command/Query 分離型 API クライアント | 作成済み |
| [インフラストラクチャ](architecture_infrastructure.md) | Heroku デプロイ、Aiven Kafka、ローカル Docker Compose 構成 | 作成済み |

### モデル設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [データモデル設計](data-model.md) | Event Store（JPA/PostgreSQL）、Read Model、Saga Store、Auth DB の ER 図 | 作成済み |
| [ドメインモデル設計](domain-model.md) | コンテキスト、集約、エンティティ、値オブジェクト、ドメインイベント | 作成済み |

### UI/UX設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [UI 設計](ui_design.md) | 画面設計、画面遷移図、コンポーネント設計 | 作成済み |

### 品質・運用

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [テスト戦略](test_strategy.md) | テストピラミッド、Kafka Testcontainers、品質ゲート | 作成済み |
| [非機能要件](non_functional.md) | 性能、可用性、セキュリティ、保守性、拡張性 | 作成済み |
| [運用要件](operation.md) | 監視（Heroku ログ）、Aiven Kafka 保持、Heroku Postgres バックアップ | 作成済み |

### その他

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [技術スタック選定](tech_stack.md) | Axon Kafka Extension・Aiven・Heroku を含む技術一覧 | 作成済み |

## 補足

- 実ドキュメントを追加したら、この一覧と `docs/index.md` を更新します。
- take-4 との主な差分: Axon Server 廃止 → Axon Kafka Extension + Aiven Kafka 採用、AWS ECS → Heroku 移行。
