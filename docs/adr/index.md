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
| [ADR-0012](0012-cross-service-idempotency-and-transactions.md) | cross-service イベントの冪等性とトランザクション境界（集約発火型 + 投影フラグ列 + 待避テーブル） | 実装中（IT6/IT7 で trackingms/handlingms/billingms に適用、自己整合チェックリスト追記済） |
| [ADR-0013](0013-public-tracking-token.md) | 公開追跡照会の時限署名トークン（JWT HS256、有効期限 30 日、Heroku Config Vars → IT8 で AWS Secrets Manager） | 提案中 |
| [ADR-0014](0014-processing-group-naming.md) | Axon @ProcessingGroup 命名規約（`cross-` / `local-` / `outbound-` の 3 種類 prefix） | 提案中 |
| [ADR-0015](0015-billingms-cross-service-and-shipper-acl.md) | billingms の cross-service 連携と ShipperInfo ACL（Kafka tracking + 集約発火型 + REST 同期 + Resilience4j + Caffeine cache TTL 5min） | 部分実装済み（IT7 完了、ShipperInfoAcl/Rest は IT8） |
| [ADR-0016](0016-processing-group-renaming.md) | 既存 @ProcessingGroup の一斉改名 + token 移行手順（環境別）+ ArchUnit 構造ガード | 提案中 |
| [ADR-0017](0017-overdue-scheduler-cluster-lock.md) | OverdueScheduler のクラスタ排他制御方針（ShedLock + JDBC、IT8 で確定） | 提案中 |
| [ADR-0018](0018-notification-adapter-selection.md) | 通知アダプタ選定（SendGrid Add-on + Dynamic Templates、IT8 で確定） | 提案中 |
| [ADR-0019](0019-payment-detail-recorded-event.md) | PaymentDetailRecorded 補完イベント（shared 最小契約 + 内部詳細の分離、IT8 で実装） | 提案中 |
| [ADR-0020](0020-payment-gateway-webhook.md) | 決済機関 webhook 受信設計（Stripe + HMAC 署名検証 + idempotency キー + 部分入金、IT9 で実装） | 提案中 |

ADR の作成には `creating-adr` スキルを使用してください。
