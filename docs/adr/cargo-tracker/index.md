# cargo-tracker — ADR

cargo-tracker プロジェクトのADRドキュメントです。

## ドキュメント一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) | CQRS / Event Sourcing を Axon Framework 5 でマイクロサービスとして実装する | 提案 |
| [ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md) | Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする | 提案 |
| [ADR-0003](0003-crypto-shredding-for-personal-data.md) | 個人情報は荷主ごとの鍵で暗号化し、削除要求には鍵の破棄（crypto-shredding）で応じる | 提案 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
