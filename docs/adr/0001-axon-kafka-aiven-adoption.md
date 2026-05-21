# ADR-0001 メッセージング基盤として Axon Kafka Extension + Aiven Managed Kafka を採用する

国際貨物輸送管理システムのマイクロサービス間イベント配信基盤として、**Axon Framework 5 + Axon Kafka Extension + Aiven Managed Kafka** を採用する。

日付: 2026-05-21

## ステータス

承認済み

## コンテキスト

本プロジェクト（take-5）は take-4 の Axon Framework 5 + Axon Server 構成を出発点とし、以下の課題に対応するためメッセージング基盤を変更する。

- **Axon Server の運用コスト**: Axon Server はステートフルなコンテナであり、EBS / EFS への永続化、1h ごとのスナップショット、障害復旧手順など運用責任が大きい。Heroku への移行に際し、Heroku は EC2 起動タイプのような「EBS マウント可能なステートフルコンテナ」の運用には適していない
- **デプロイ先変更**: AWS ECS から **Heroku** に移行するため、Axon Server のような長期永続 EBS を必要とするサービスの運用が困難になる
- **マネージドサービスの活用**: Kafka をマネージドで提供する **Aiven** を利用することで、ブローカー管理・TLS 設定・バックアップを Aiven に委ねられる
- **コスト削減**: AWS ECS 構成（月額 ~$1,070）から Heroku + Aiven 構成（月額 ~$89）への大幅削減

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| Axon Server（take-4 の構成） | Event Store + バス統合。テスト・Saga 管理が簡潔 | ステートフル。EBS 必須。Heroku では運用困難 |
| Axon Kafka Extension + Aiven | Kafka の高スループット。マネージドで運用不要。Heroku から SSL 接続可能 | Event Store は別途 JPA/PostgreSQL で管理。Kafka の概念的オーバーヘッド |
| Spring Cloud Stream + Kafka | Kafka 標準連携。Spring エコシステムと親和 | CQRS / Saga の実装ボイラープレートが増える |

## 決定

**Axon Framework 5 + Axon Kafka Extension + Aiven Managed Kafka を採用する。**

具体的には以下のとおりとする。

- **イベントバス**: Axon Kafka Extension（`axon-kafka-spring-boot-starter`）でイベントを Aiven Kafka に発行・購読する
- **Event Store**: Axon の `JpaEventStorageEngine`（PostgreSQL バック）を使用し、Heroku Postgres に永続化する
- **コマンドバス**: サービス内ローカル処理（`SimpleCommandBus`）
- **Token Store / Saga Store**: `JdbcTokenStore` / `JdbcSagaStore`（Heroku Postgres 共用）
- **本番環境**: Heroku（各サービスを Eco dyno として個別デプロイ）
- **Kafka 接続**: Aiven が提供する SSL 接続情報を Heroku Config Vars に設定

### Axon Kafka 設定例

```yaml
axon:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    default-topic: cargo-events
    producer:
      event-processor-mode: tracking
    properties:
      security.protocol: SSL
      ssl.truststore.location: /etc/ssl/certs/ca-certificates.crt
  eventhandling:
    processors:
      default:
        mode: tracking
```

### Heroku Config Vars（各サービス共通）

| 変数名 | 説明 |
| :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Aiven Kafka のホスト:ポート |
| `KAFKA_SECURITY_PROTOCOL` | `SSL` |
| `SPRING_PROFILES_ACTIVE` | `heroku` |
| `JAVA_TOOL_OPTIONS` | JVM メモリ制限（`-XX:MaxRAMPercentage=50.0`等） |

## 影響

### ポジティブ

- Axon Server の EBS 管理・スナップショット・障害復旧手順が不要になる
- Aiven のマネージドサービスにより Kafka ブローカーの運用責任がなくなる
- Heroku への移行が実現し、インフラコストが月額約 $89 に削減される
- Axon Framework の CQRS / Event Sourcing / Saga の恩恵はそのまま維持される

### ネガティブ

- Event Store が Axon Server から JPA/PostgreSQL に変わるため、Axon Server の管理 UI（イベント確認・リプレイ）が使えなくなる
- Kafka のトピック設計・パーティション設計の責任がチームに生じる
- SSL 証明書の管理（Aiven CA 証明書のコンテナへの配布）が必要
- Axon Kafka Extension の Axon Framework 5 / Spring Boot 4 対応状況を実装着手前に確認する必要がある

## コンプライアンス

- 各マイクロサービスが Aiven Kafka に SSL 接続してイベントを発行・購読できること
- Axon の Saga が Kafka 経由のイベントで正常に動作すること
- `./gradlew test` でユニットテスト・統合テストがすべて PASS すること
- `npx gulp deploy:dev` で Heroku へのデプロイが完了すること

## 備考

- 著者: k2works
- 関連 ADR: ADR-0006（Heroku Container Registry デプロイ構成）
- 参考: take-4 ADR-0001（Axon Server 採用の経緯・評価）
