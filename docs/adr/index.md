# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](001-nestjs-as-application-framework.md) | アプリケーションフレームワークとして NestJS を採用する | 承認済み |
| [ADR-002](002-kysely-and-node-pg-migrate.md) | データアクセスに Kysely、マイグレーションに node-pg-migrate を採用する | 承認済み |
| [ADR-003](003-tsx-ssr-with-htmx.md) | フロントエンドを TSX SSR + htmx で構成する | 承認済み |
| [ADR-004](004-pgmem-local-testcontainers-ci.md) | ローカル開発は pg-mem、テストの正は Testcontainers PostgreSQL とする | 承認済み |
| [ADR-005](005-event-emitter-context-integration.md) | コンテキスト間連携は NestJS EventEmitter による同一プロセス内イベントとする | 承認済み |
| [ADR-006](006-session-auth-without-passport.md) | 認証はセッションベースの自作ガードとし Passport を採用しない（実行は SWC/tsc） | 承認済み |
| [ADR-007](007-shared-kernel-and-stub-acl.md) | 共有カーネルに Location/CargoType を配置、Routing 候補算出は外部経路 ACL へ段階移行 | 承認済み |
| [ADR-008](008-routing-candidate-port-boundary.md) | 見積概算候補と経路候補 Port を分離、Booking の RouteCandidateAcl で経路紐付け、追跡番号は Booking 側で暫定採番 | 承認済み |
| [ADR-009](009-post-commit-side-effects.md) | コミット後副作用（通知・イベント）はコマンド失敗として扱わず、冪等リスナーと監視で結果整合を担保 | 承認済み |
| [ADR-010](010-customs-declaration-aggregate.md) | 通関申告を独立集約に昇格し、読みモデルの DB 直依存を意図的例外とする | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
