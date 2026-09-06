# cargo-tracker — ADR

cargo-tracker プロジェクトのADRドキュメントです。

## ドキュメント一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) | CQRS / Event Sourcing を Axon Framework 5 でマイクロサービスとして実装する | 提案 |
| [ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md) | Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする | 提案 |
| [ADR-0003](0003-crypto-shredding-for-personal-data.md) | 個人情報は荷主ごとの鍵で暗号化し、削除要求には鍵の破棄（crypto-shredding）で応じる | 提案 |
| [ADR-0004](0004-demo-login-for-development.md) | 開発環境のログイン画面に動作確認用の利用者を事前入力する | 提案 |
| [ADR-0005](0005-flyway-locations-per-service.md) | Flyway のマイグレーションをサービス名のサブディレクトリに分ける | 提案 |
| [ADR-0006](0006-role-authorization-at-the-gateway.md) | ロールの認可を Gateway の 1 か所で宣言する | 提案 |
| [ADR-0007](0007-route-search-cutoff.md) | 経路探索を乗り継ぎ 3 回・候補 20 件で打ち切る | 提案 |
| [ADR-0008](0008-cargo-revision-as-a-projection.md) | 予約の修正内容は投影として持つ | 提案 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
* [ADR-0009 営業への差し戻しを状態遷移にしない](./0009-condition-review-is-not-a-state-transition.md) - 条件では組めないことを営業へ返すとき、経路設計の状態を戻さず記録で表す。差し戻せる状態と、条件調整が経路設計をやり直しにすることも併せて決める。
* [ADR-0010 サービスをまたぐ連鎖の調整役を Reaction Handler に一本化する](./0010-reaction-handler-as-the-only-coordinator.md) - 予約から追跡開始までの連鎖を BookingReactionHandler + processstate で表し、追跡番号の採番と発行者、そして補償の粒度を決める。
