# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-axon-framework-adoption.md) | メッセージング基盤として Axon Framework 5 を採用する | 受け入れ済み |
| [ADR-0002](0002-mybatis-adoption.md) | データアクセスとして MyBatis を採用する | 受け入れ済み |
| [ADR-0003](0003-phase0-skeleton-and-ghcr-adoption.md) | Phase 0 雛形生成とコンテナレジストリとして GHCR を採用する | 受け入れ済み |
| [ADR-0004](0004-microservice-decomposition.md) | マイクロサービスをバウンデッドコンテキスト単位で分割する | 承認済み |
| [ADR-0005](0005-shared-module-role.md) | shared モジュールは共有カーネルとして Location・UnLocode のみを提供する | 承認済み |
| [ADR-0006](0006-heroku-deployment-setup.md) | Heroku Container Registry を用いた開発環境デプロイ構成を採用する | 承認済み |
| [ADR-0007](0007-axon-5-event-sourcing-api.md) | Axon Framework 5.1 の Event Sourcing 採用と新アノテーション API への対応 | 承認済み |
| [ADR-0008](0008-axon-5-spring-boot-integration-pattern.md) | Axon 5.1 Aggregate を Spring Boot 4 に統合する具体パターン（`@EventSourced` + `subscribing` モード） | 承認済み |
| [ADR-0009](0009-axon-server-connector-explicit-dependency.md) | `axon-server-connector` を明示依存にし `local-docker` を pooled-streaming に復帰させる | 承認済み |
| [ADR-0010](0010-us08-poc-promotion-policy.md) | US08 先行スパイク PoC の処理方針（テストは残し実装は IT4 でゼロから書き直す） | 承認済み |
| [ADR-0011](0011-carrier-movement-and-transit-edge-responsibility.md) | `CarrierMovement`（Write Side VO）と `TransitEdge`（Read Side Query VO）の責務分離 | 承認済み |
| [ADR-0012](0012-handlingms-trackingms-responsibility-separation.md) | handlingms と trackingms の責務分離・Saga 適用方針 | 提案 |
| [ADR-0013](0013-tracking-number-jwt-time-limited-token.md) | Tracking Number JWT 時限トークン設計（HMAC-SHA256・exp 30 日・delivered_at + 7 日失効） | 提案 |
| [ADR-0014](0014-shared-module-event-classes.md) | shared モジュールへの Event クラス集約と各マイクロサービスへの依存方針 | 提案 |

ADR の作成には `creating-adr` スキルを使用してください。
