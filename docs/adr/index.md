# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-axon-kafka-aiven-adoption.md) | メッセージング基盤として Axon Kafka Extension + Aiven Managed Kafka を採用する | 承認済み |
| [ADR-0002](0002-mybatis-adoption.md) | データアクセスとして MyBatis を採用する | 承認済み |
| [ADR-0006](0006-heroku-deployment-setup.md) | Heroku Container Registry を用いた開発環境デプロイ構成 | 承認済み |
| [ADR-0007](0007-unify-db-initialization-with-flyway.md) | `local-h2` を含む DB 初期化を Flyway に統一し、`schema.sql` を廃止する | 承認済み |
| [ADR-0008](0008-pagination-strategy.md) | 一覧 API にページネーション (Offset/Limit + PageResponse) を採用する | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
