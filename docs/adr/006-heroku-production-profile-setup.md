# ADR-006: 本番 Heroku 環境のプロファイル設計と CloudAMQP 接続セットアップ手順

Heroku デプロイ環境（`product` プロファイル）の構成として、DB は H2 メモリ DB、メッセージングは CloudAMQP (AMQPS / TLS) を使う方針と、それを実現するための環境変数受け渡し手順を確立する。

日付: 2026-05-12

## ステータス

承認済み

## コンテキスト

Heroku 上の bookingms / trackingms で以下の障害が連続して発生した。

- **R1**: `Attempting to connect to: [localhost:5672]` で RabbitMQ 接続失敗
  - 原因: `SPRING_PROFILES_ACTIVE=product` が未設定で、default プロファイル（H2＋localhost RabbitMQ 既定値）で起動していた
- **R2**: `product` プロファイル有効化後も RabbitMQ に接続できない
  - 原因: CloudAMQP は単一 URL（`CLOUDAMQP_URL`）を提供するが、`application-product.yml` は `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` の個別変数を読む設計で、URL のままでは Spring に渡らない
- **R3**: PostgreSQL 接続エラー（`FATAL: password authentication failed`）
  - 原因: `application-product.yml` の datasource は PostgreSQL 既定で、Heroku に PostgreSQL アドオンを置かない方針のため接続先がない
- **R4**: CloudAMQP に到達するも `EOFException` で切断
  - 原因: CloudAMQP の URL は `amqps://`（TLS、ポート 5671）だが、Spring AMQP のデフォルトは平文 AMQP で TLS ハンドシェイクを行っていない
- **R5**: `R14 (Memory quota exceeded)` で 512MB dyno 上限を超過（実測 570MB / 111%）
  - 原因: Spring Boot 4 / Java 25 の既定では `MaxRAMPercentage=75` で約 384MB ヒープ＋メタスペース・コードキャッシュ・スレッドスタックの非ヒープ領域で 512MB を超える
- **R6**: メモリ削減のため `spring.main.lazy-initialization=true` を入れたところ、bookingms の `@RabbitListener` がキューを subscribe しなくなり、`TrackingNumberIssuedEvent` を受信できず予約ステータスが `TRACKING_ISSUED` に遷移しない
  - 原因: 遅延初期化が `SimpleMessageListenerContainer` まで対象とし、起動時にコンシューマが生成されない
- **R7**: 荷役登録 `POST /api/handling/v1/activities` が 404 を返す
  - 原因: gatewayms の `HANDLINGMS_URL` が handlingms（スケルトン状態・コントローラ未実装）の Heroku URL に向いていた。実体は trackingms に同居している

加えて、以下の前提条件が確認できた。

- 案件は MVP / デモ用途で、データの永続化は必須ではない（dyno 再起動でリセットしても許容）
- 既に [ADR-001](./001-heroku-api-routing-and-cors.md) で Heroku 上の API ルーティング / CORS は環境変数駆動に統一済み
- Heroku 上の CloudAMQP アドオンは既に追加されており、`bookingms` をプライマリとして共有する運用

## 決定

### プロファイル設計

3 階層のプロファイルで、ローカル / ローカル本番擬似 / クラウド本番 の使い分けを明確にする。

| プロファイル | DB | RabbitMQ | 用途 |
| --- | --- | --- | --- |
| default | H2 メモリ DB | localhost（任意） | ローカル開発（最速イテレーション） |
| local-prod | ローカル Docker PostgreSQL:5442 | ローカル Docker RabbitMQ:5672 | ローカルでの本番擬似検証（[`docker-compose`](../../apps/docker-compose.yml) 経由） |
| **product** | **H2 メモリ DB** | **CloudAMQP (AMQPS / TLS)** | **Heroku デプロイ** |

`product` プロファイルでも将来 PostgreSQL 等に切り替えられるよう、`application-product.yml` の datasource は `DB_URL` / `DB_DRIVER` / `DB_USERNAME` / `DB_PASSWORD` 環境変数でオーバーライドできる構造を維持する。既定値が H2 になっている点が従来との違い。

### CloudAMQP URL の取り扱い

`CLOUDAMQP_URL`（`amqps://user:pass@host:port/vhost` 形式）をデプロイスクリプトでパースし、Spring Boot 互換の個別環境変数に分解して各メッセージングサービスに配布する。

| 環境変数 | 由来 | application-product.yml の参照 |
| --- | --- | --- |
| `RABBITMQ_HOST` | URL の hostname | `spring.rabbitmq.host` |
| `RABBITMQ_PORT` | URL の port（amqps なら 5671） | `spring.rabbitmq.port` |
| `RABBITMQ_USERNAME` | URL の userinfo | `spring.rabbitmq.username` |
| `RABBITMQ_PASSWORD` | URL の userinfo | `spring.rabbitmq.password` |
| `RABBITMQ_VIRTUAL_HOST` | URL の pathname（CloudAMQP では必須） | `spring.rabbitmq.virtual-host` |
| `RABBITMQ_SSL_ENABLED` | URL protocol が `amqps:` なら true | `spring.rabbitmq.ssl.enabled` |

### メモリ最適化方針（512MB dyno）

R5 で観測したメモリ超過に対し、以下を採用する。

- **JVM オプション（`JAVA_OPTS`）**: Heroku Eco/Basic（512MB）に収めるためヒープ・非ヒープを明示的に制限
  - `-XX:MaxRAMPercentage=50.0`（ヒープ上限 ≈ 256MB）
  - `-XX:ReservedCodeCacheSize=64m`
  - `-XX:MaxMetaspaceSize=128m`
  - `-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo`
- **`spring.jmx.enabled: false`**: 運用上不要な JMX エクスポートを無効化
- **`spring.main.lazy-initialization: true` は採用しない**: R6 の通り `@RabbitListener` のメッセージリスナーコンテナが起動時に subscribe しなくなるため。AMQP / Scheduler / WebSocket など非同期コンポーネントを持つサービスでは副作用が大きい

### Gateway ルーティング（handlingms 暫定対応）

`HandlingActivityController` は現在 trackingms 内に同居しており、handlingms はディレクトリと `HandlingApplication.java` だけのスケルトン。`gatewayms/application.yml` の既定値だけでなく Heroku の `HANDLINGMS_URL` 環境変数も trackingms の URL に向ける。

| 環境変数 | 通常の用途 | 暫定対応の値 |
| --- | --- | --- |
| `TRACKINGMS_URL` | trackingms の Web URL | trackingms の Web URL |
| `HANDLINGMS_URL` | handlingms の Web URL（独立時） | **trackingms の Web URL（暫定）** |

handlingms 独立後は `HANDLINGMS_URL=${handlingUrl}` に戻す。

### 変更箇所

- **`apps/backend/{authms,billingms,bookingms,handlingms,routingms,trackingms}/src/main/resources/application-product.yml`**
  - datasource の既定値を PostgreSQL から H2 メモリ DB に変更
  - bookingms / trackingms には `spring.rabbitmq.{host,port,username,password,virtual-host,ssl.enabled,listener.simple.missing-queues-fatal}` を追加（CloudAMQP TLS 対応）
  - 全サービスで `spring.jmx.enabled: false` を設定（メモリ削減）
- **`ops/scripts/deploy_dev.js`**
  - `parseAmqpUrl()` ヘルパー追加（amqps:// 判定と URL デコード）
  - `deploy:dev:config` で `SPRING_PROFILES_ACTIVE=product` および `JAVA_OPTS`（メモリ最適化版）を全バックエンドサービスに設定
  - `deploy:dev:config` で `HANDLINGMS_URL` を `trackingUrl` に向ける（暫定）
  - `deploy:dev:amqp:share` で `CLOUDAMQP_URL` をパースし、プライマリも含む全メッセージングサービスに `RABBITMQ_*` を個別設定（`RABBITMQ_SSL_ENABLED` 含む）

### デプロイ手順（標準フロー）

1. **既存環境変数のクリーンアップ**（過去に `DB_URL=postgresql://...` 等を設定していた場合のみ）

   ```bash
   heroku config:unset DB_URL DB_USERNAME DB_PASSWORD DB_DRIVER -a <app>
   ```

2. **Docker イメージのビルド・プッシュ・リリース**（通常のデプロイフロー）

3. **環境変数の設定**

   ```bash
   npx gulp deploy:dev:config        # SPRING_PROFILES_ACTIVE=product と JAVA_OPTS を配布
   npx gulp deploy:dev:amqp:share    # CLOUDAMQP_URL を RABBITMQ_* に分解して配布
   ```

4. **dyno 再起動**

   ```bash
   heroku restart -a <app>
   ```

### 代替案

- **代替案 1: Heroku Postgres を `product` で使う**
  - 却下理由: MVP / デモ用途で永続化が必須でない一方、アドオンのコストとセットアップ手間が発生する。`DB_URL` 等の環境変数でオーバーライド可能な構造は維持しているため、必要になった時点で切り替えれば良い
- **代替案 2: `spring.rabbitmq.addresses` に `CLOUDAMQP_URL` を直接渡す**
  - 却下理由: Spring AMQP の URI パース挙動が `amqps://` の TLS 自動有効化を含めて一貫しないバージョンがあり、トラブルシュート時に「URI のパースが正しいか」「TLS が有効か」を切り分けにくい。個別変数に分解して `spring.rabbitmq.ssl.enabled` を明示的に渡す方が起動時の動作が宣言的で観測しやすい
- **代替案 3: CloudAMQP の代わりに dyno 内に RabbitMQ を立てる**
  - 却下理由: Heroku dyno はステートレス前提で、複数 dyno 間でのメッセージング状態を共有できない。マネージドサービスを使うのが妥当
- **代替案 4: メモリ削減のため `spring.main.lazy-initialization: true` を有効化する**
  - 却下理由（R6 で実証済み）: `SimpleMessageListenerContainer` も遅延初期化の対象となり、起動時に `@RabbitListener` がキューを subscribe しない。AMQP メッセージを受信できず予約ステータスが遷移しないクリティカルな機能不全を引き起こす
- **代替案 5: dyno を Standard-2X（1024MB）にアップグレードしてメモリ問題を回避**
  - 却下理由: 月額コストが約 2 倍になる。MVP / デモ用途では `MaxRAMPercentage=50` + `jmx.enabled=false` の組み合わせで 512MB に収まる見込みのため、まずは無料/低価格 dyno のままで運用する
- **代替案 6: `HANDLINGMS_URL` を handlingms に維持し、handlingms を実装する**
  - 却下理由: スコープが本 ADR を大きく超える。trackingms から `HandlingActivityController` と関連サービス・ドメインを抽出する大規模リファクタが必要。handlingms 独立の機運が高まった段階で別 ADR で扱う

## 影響

### ポジティブ

- Heroku 上での起動失敗（`localhost:5672` 接続、PostgreSQL 認証、EOFException）を一度に解消できる
- 「プロファイル設計」が明文化され、`product` を「クラウド本番想定」、`local-prod` を「ローカル本番擬似検証」、`default` を「ローカル開発」と役割分離できる
- `CLOUDAMQP_URL` を環境変数として保持しつつ、Spring Boot 互換の `RABBITMQ_*` も配布することで、Spring 経由・直接アクセス両方の使い方に対応できる
- デプロイ手順が Gulp タスク化されているため、新しい Heroku アプリへの展開時も手順がコード化されている

### ネガティブ

- H2 メモリ DB のため、Heroku dyno 再起動時にデータが失われる（デモ用途では許容）
- `DB_URL` 等の環境変数が過去に設定されていると yml の既定値が効かないため、運用ドキュメントに「`config:unset` が必要なケース」を明記する必要がある
- `RABBITMQ_*` の各値を 6 種類設定するため、CloudAMQP の認証情報がローテーションされた場合は `deploy:dev:amqp:share` を再実行して全サービスに再配布する必要がある（手順は同じ）
- 512MB dyno に収めるため `MaxRAMPercentage=50` まで絞っている。将来的に機能追加や同時接続数が増えるとヒープ不足になる可能性があり、その時点で Standard-2X へのアップグレードを検討する必要がある
- `HANDLINGMS_URL=${trackingUrl}` の暫定対応は handlingms 独立時に戻し忘れるリスクがある。本 ADR と関連スクリプト（`deploy_dev.js`）双方にコメントを残して、戻すタイミングを明示している

## コンプライアンス

以下を満たすことで決定が正しく実装されていることを確認する。

- `heroku config -a cargo-tracker-3-bookingms` に以下が含まれること
  - `SPRING_PROFILES_ACTIVE=product`
  - `JAVA_OPTS` に `-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m` が含まれる
  - `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_VIRTUAL_HOST` / `RABBITMQ_SSL_ENABLED`
  - `DB_URL` 等が設定されていない、または H2 を指している
- `heroku config -a cargo-tracker-3-gatewayms` で `HANDLINGMS_URL` が trackingms の Web URL（`https://cargo-tracker-3-trackingms-*.herokuapp.com`）になっていること
- bookingms / trackingms 起動ログで以下が確認できること
  - `The following 1 profile is active: "product"`
  - `Starting embedded database url='jdbc:h2:mem:...'`
  - `Created new connection: rabbitConnectionFactory#... to <cloudamqp host>:5671`
  - bookingms のみ: 起動時に `SimpleMessageListenerContainer` のコンシューマ起動ログ（`Restarting Consumer@...` 等）が出ること（リスナーが subscribe している証拠）
- `heroku ps -a cargo-tracker-3-bookingms` で `Error R14 (Memory quota exceeded)` が出ていないこと
- 経路割当・予約確定・**追跡番号発行（ステータス遷移含む）**・荷役登録のエンドツーエンドフローが Heroku 上で 200 OK で完了すること

## 更新履歴

| 日付 | 内容 |
| --- | --- |
| 2026-05-12 | 初版作成（R1〜R4: profile 未設定 / CloudAMQP URL 形式 / PostgreSQL 接続 / TLS 未設定） |
| 2026-05-12 | R5（メモリ超過）対応として `JAVA_OPTS` 調整と `spring.jmx.enabled=false` を追記 |
| 2026-05-12 | R6（lazy-initialization で `@RabbitListener` が subscribe しない）対応を追記。代替案 4 として却下理由を明記 |
| 2026-05-12 | R7（HANDLINGMS_URL の暫定対応）と handlingms 独立時に戻すべき事項を追記 |

## 備考

- 著者: -
- 関連 ADR: [ADR-001](./001-heroku-api-routing-and-cors.md)（Heroku API ルーティング・CORS）, [ADR-004](./004-testcontainers-rabbitmq-integration-test.md)（RabbitMQ 統合テスト）, [ADR-005](./005-tracking-number-issued-event-contract.md)（TrackingNumberIssuedEvent 契約管理）
- 関連スクリプト: `ops/scripts/deploy_dev.js`
