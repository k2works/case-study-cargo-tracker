# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](./001-java-25-spring-boot-4.md) | Java 25 LTS + Spring Boot 4.0 を採用する | 承認済み |
| [ADR-002](./002-handling-context-tracking-integration.md) | Handling Context を Tracking Context に統合する | **置き換え済み（ADR-010）** |
| [ADR-003](./003-database-strategy.md) | 開発は H2、Repository のテストは Testcontainers を使う | 承認済み（2026-08-06 改訂 2 回） |
| [ADR-004](./004-mybatis-not-jpa.md) | 永続化に MyBatis を採用し JPA / Hibernate を採用しない | 承認済み |
| [ADR-005](./005-shared-kernel-scope.md) | 共有カーネルの範囲を Location と ShipperId に限定する | 承認済み |
| [ADR-006](./006-external-integration-internal-simulation.md) | 外部システム連携は実装せず内部シミュレーションで代替する | 承認済み |
| [ADR-007](./007-security-supporting-subdomain.md) | 認証・認可を支援サブドメイン `security` に置く | 承認済み |
| [ADR-008](./008-freight-cost-estimation.md) | 経路候補の費用は概算式で算出する | 承認済み |
| [ADR-009](./009-domain-events-for-cross-context-propagation.md) | BC 間の状態伝播はドメインイベントによる結果整合で行う | 承認済み（2026-08-08 改訂 1 回） |
| [ADR-010](./010-handling-as-independent-context.md) | Handling Context を独立した BC に昇格する（**ADR-002 を置き換え**） | 承認済み |
| [ADR-011](./011-public-endpoint-rate-limit.md) | 公開エンドポイントの防御を単一プロセス内のレートリミットで行う | 承認済み |
| [ADR-012](./012-cross-context-dependency-direction.md) | BC 間の依存の向きを一方通行に保ち、残る循環はインフラ層に閉じ込める | 承認済み |
| [ADR-013](./013-user-shipper-link.md) | 利用者と荷主の紐付けは Security Context が共有カーネルの ShipperId だけで持つ | 承認済み |
| [ADR-014](./014-dashboard-contribution-by-context.md) | ダッシュボードの件数は各 BC が `@ControllerAdvice` で自分で載せる | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
