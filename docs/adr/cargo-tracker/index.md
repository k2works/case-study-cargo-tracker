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
| [ADR-0008](0008-cargo-revision-as-a-projection.md) | 予約の修正内容は投影として持つ | 提案 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
