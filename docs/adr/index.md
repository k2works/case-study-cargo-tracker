# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-モジュール構成は垂直スライスを採用.md) | モジュール構成は垂直スライス（コンテキストファースト）を採用 | 承認済み |
| [ADR-0002](0002-ドメインイベントはPayloadレコード方式とpost-commitディスパッチを採用.md) | ドメインイベントは Payload レコード方式 + post-commit ディスパッチを採用 | 承認済み |
| [ADR-0003](0003-DBマイグレーションはDbUpによるforward-only方式を採用.md) | DB マイグレーションは DbUp による forward-only 方式を採用 | 承認済み |
| [ADR-0004](0004-Donaldによる集約永続化パターンを採用.md) | Donald による DDD 集約の永続化パターン（手書き SQL・楽観ロック）を採用 | 提案 |
| [ADR-0005](0005-Cookie認証とuser_rolesによるRBACを採用.md) | Cookie 認証 + `users`/`user_roles` による RBAC を採用 | 提案 |
| [ADR-0006](0006-時刻とGUIDの注入ポートを採用.md) | 時刻・GUID の注入ポート（Clock / IdGenerator）を採用 | 提案 |
| [ADR-0007](0007-経路設計中状態はBookingState_DU拡張で表現.md) | 「経路設計中」状態は BookingState DU の拡張（RoutingRequested ケース）で表現 | 承認済み |
| [ADR-0008](0008-荷主参照はShipperId永続化により業務識別子で行う.md) | 荷主の横断参照は ShipperId（Guid）の永続化により業務識別子で行う | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
