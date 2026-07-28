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

ADR の作成には `creating-adr` スキルを使用してください。
