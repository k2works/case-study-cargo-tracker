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
| [ADR-0009](0009-経路候補算出はRouting自コンテキストで構成する.md) | 経路候補算出は Routing Context が自コンテキストの Voyage スケジュールから構成する | 提案 |
| [ADR-0010](0010-経路確定のRouting_Booking連携は合成層のACL変換で行う.md) | 経路確定の Routing→Booking 連携は合成層の ACL 変換で行う | 提案 |
| [ADR-0011](0011-追跡照会の所有者制御はcapabilityトークンとロールで行う.md) | 追跡照会の所有者制御は capability トークン（公開）とロール（認証）で行う | 提案 |
| [ADR-0012](0012-荷役から追跡への状態連携はベストエフォートと冪等で行う.md) | 荷役→追跡の状態連携はベストエフォート＋冪等（結果整合）で行う | 提案 |
| [ADR-0013](0013-料金算出とBilling_Booking連携は合成層と状態射影で行う.md) | 料金算出と Billing↔Booking 連携は合成層 ACL と状態射影で行う | 提案 |
| [ADR-0014](0014-決済ACLはPaymentGatewayPortとWireMockで契約固定する.md) | 決済 ACL は PaymentGatewayPort で抽象化し HTTP 契約スタブで契約固定する | 承認 |

ADR の作成には `creating-adr` スキルを使用してください。
