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
| [ADR-0009](0009-cross-service-event-saga.md) | cross-service イベント連携と Axon Saga を採用する（Kafka tracking モード） | 承認済み |
| [ADR-0010](0010-local-h2-kafka-topic-initialization.md) | `local-h2` のインメモリ event store と Kafka トピックを整合させる（トピック初期化と冪等な孤児イベント処理） | 承認済み |
| [ADR-0011](0011-kafka-tracking-error-handling-policy.md) | Kafka tracking プロセッサのエラーハンドリング統一方針（ホワイトリスト方式の継続と伝播先処理の標準化） | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
