# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-集約永続化戦略.md) | Dapper による集約永続化戦略（全削除→再挿入・マルチクエリ再構築・楽観的ロック） | 承認 |
| [ADR-0002](0002-UnitOfWorkとpost-commitイベントディスパッチ.md) | Unit of Work とドメインイベントの post-commit ディスパッチ | 承認 |
| [ADR-0003](0003-開発SQLite本番PostgreSQLの二方言運用.md) | 開発 SQLite / 本番 PostgreSQL の二方言運用（B 案 + 緩和策） | 承認 |
| [ADR-0004](0004-Cookie認証と軽量ユーザーストア.md) | Cookie 認証と Dapper 軽量ユーザーストア（full Identity 不使用） | 承認 |
| [ADR-0005](0005-CQRSの段階的適用.md) | CQRS の段階的適用（サービス分離・単一 DB・イベントソーシングなし） | 承認 |
| [ADR-0006](0006-AmbientTransactionによるトランザクション伝播.md) | Ambient Transaction によるトランザクション伝播（ADR-0002 の Transaction 公開を Supersede） | 承認 |
| [ADR-0007](0007-貨物種別と経路候補のBC独立定義.md) | 貨物種別・経路候補の BC 独立定義（共有カーネルへ昇格しない） | 承認 |
| [ADR-0008](0008-外部経路サービスの契約方針.md) | 外部経路サービスの契約方針（ローカル算出を正式化・実連携時に WireMock 契約） | 承認 |
| [ADR-0009](0009-post-commitイベント連鎖の結果整合性方針.md) | post-commit イベント連鎖の結果整合性方針（冪等・失敗ログ・手動修復・Outbox 移行） | 承認 |

ADR の作成には `creating-adr` スキルを使用してください。
