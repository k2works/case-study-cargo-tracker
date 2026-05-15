# ADR-0009: Axon Server Connector を明示依存にし pooled-streaming に復帰する

`org.axonframework:axon-server-connector` を bookingms / routingms の依存に明示追加し、`application-local-docker.yml` の `subscribing` 上書きを削除して PooledStreamingEventProcessor を有効化する。Maven Central の公開状況に合わせて Axon ライブラリ群（starter / axon-test / connector）を一括 `5.1.0-RC2` に揃える。

日付: 2026-05-15

## ステータス

承認済み

## コンテキスト

ADR-0008 で `local-docker` プロファイルの Event Processor は `pooled-streaming`、`local-h2` のみ `subscribing` とする方針を確定したが、IT3 着手前の動作確認で **Axon Server コンテナを停止しても bookingms / routingms が正常応答する** という重大な構成不全が判明した。

### 発覚の経緯

1. `docker stop cargo-axonserver` の状態で `POST /api/v1/bookings` を発行すると、稼働中と同等の 78ms で `201 Created` が返却された（稼働中 85ms、ほぼ同等）。
2. アクセスログを追うと、同一 HTTP スレッド内で `ShipperMapper.existsById → CargoSummaryMapper.insert` が完結しており、本来非同期で動作するはずの `CargoBookedEvent → CargoProjectionsEventHandler → cargo_summary INSERT` が同期実行されていた。
3. 起動ログに `Connected to AxonServer` や `org.axonframework.axonserver.connector` 系のメッセージが一切なく、Axon Server への gRPC 接続が確立されていない可能性が高い。

### 原因の調査

`./gradlew :bookingms:dependencies --configuration runtimeClasspath` で transitive を全展開した結果、`org.axonframework.extensions.spring:axon-spring-boot-starter:5.1.0` の依存に `axon-server-connector` が**含まれていない**ことが判明した。Maven Central 上の `axon-spring-boot-starter-5.1.0.pom` を直接確認しても同様で、Axon 4.x まで transitive で提供されていた `axon-server-connector` が 5.x で **starter から切り出され、利用側が明示依存として追加する設計**に変更されている。

Spring Boot Auto Configuration の `AxonServerAutoConfiguration` は `@ConditionalOnClass(AxonServerConfiguration.class)` などで守られているため、connector が classpath に無い場合は無音でフォールバックし、`SimpleCommandBus` と in-memory EventBus で完結する。`axon.axonserver.servers: axonserver:8124` 設定は事実上無視されていた。

### ADR-0008 との関係

ADR-0008 が暫定対応として記述している「`local-docker` でも一旦 `subscribing` 上書き」も、**connector 不在のため意味を成していなかった**。`subscribing` を外して pooled-streaming に戻しても接続できないままだった可能性がある。

つまり ADR-0008 で記録された IT3 課題（`pooled-streaming` への復帰）に着手する際、`token_entry` テーブル整備に加えて **connector 明示依存の追加が前提となる**。

### Axon 5.1 の Maven Central 公開状況

`org.axonframework:axon-server-connector` の GA 5.1.0 は 2026-05-15 現在 Maven Central 未公開で、最新は `5.1.0-RC2`。`axon-spring-boot-starter` は 5.1.0 GA まで公開済み。

当初は「starter 5.1.0 GA + connector 5.1.0-RC2」で進めようとしたが、テスト実行で `NoClassDefFoundError: org/axonframework/messaging/queryhandling/distributed/QueryBusConnector` が発生した。`QueryBusConnector` インタフェースのシグネチャが GA と RC2 で食い違っており、`axon-messaging:5.1.0 GA` と `axon-server-connector:5.1.0-RC2` の **ABI 互換性が成立しない**ことが判明。Spring Boot Auto Configuration の Bean 構築段階で必ず失敗するため、テスト・本番ともに通らない。

このため、整合性を取れる唯一の組み合わせとして **Axon 系（starter / axon-test / connector）を一括 `5.1.0-RC2` に揃える**こととした。Maven Central で完全な GA 整合を取るには 5.0.x まで全面ダウングレードするしかないが、それでは ADR-0007 / ADR-0008 で確立した API（`@EventSourced` Spring stereotype など）が利用できなくなる。

## 決定

**Axon Framework 系ライブラリ（`axon-spring-boot-starter` / `axon-test` / `axon-server-connector`）を一括 `5.1.0-RC2` に揃え、`axon-server-connector` を bookingms / routingms の `build.gradle.kts` に明示依存として追加する。同時に `application-local-docker.yml` の `subscribing` 上書きを削除し、`token_entry` テーブルを Flyway で作成して PooledStreamingEventProcessor に復帰させる。**

### 変更箇所

**1. `apps/backend/gradle/libs.versions.toml`**

```toml
[versions]
# Axon 系を一括 5.1.0-RC2（GA connector 公開後に 5.1.0 へ昇格）
axon = "5.1.0-RC2"

[libraries]
axon-server-connector = { module = "org.axonframework:axon-server-connector", version.ref = "axon" }
```

**2. `apps/backend/{bookingms,routingms}/build.gradle.kts`**

```kotlin
implementation(libs.axon.spring.boot.starter)
implementation(libs.axon.server.connector)
```

**3. `AxonJdbcConfig`（新規 / bookingms と routingms 双方）**

Axon 5.1-RC2 の `JdbcAutoConfiguration` / `JdbcTransactionAutoConfiguration` は Jackson 3 (`tools.jackson.databind.ObjectMapper`) を前提とした `defaultAxonObjectMapper` を要求するため、Spring Boot 4 + Jackson 2 の現行構成では autoconfig が機能しない。Axon が必要とする JDBC インフラ Bean をすべて明示的に構成する。

```java
@Configuration
public class AxonJdbcConfig {

    @Bean
    public ConnectionProvider axonConnectionProvider(DataSource dataSource) {
        return new SpringDataSourceConnectionProvider(dataSource);  // Spring Tx 同期参加
    }

    @Bean
    public TransactionManager axonTransactionManager(
            PlatformTransactionManager platformTm, ConnectionProvider cp) {
        // 第 3 引数 ConnectionProvider が非 null であることが SUPPLIER_KEY バインドの必須条件。
        return new SpringTransactionManager(platformTm, null, cp);
    }

    @Bean
    public TokenSchema tokenSchema() {
        return TokenSchema.builder()
                .setTokenTable("token_entry")
                .setProcessorNameColumn("processor_name")
                .setSegmentColumn("segment")
                .setMaskColumn("mask")
                .setTokenColumn("token")
                .setTokenTypeColumn("token_type")
                .setTimestampColumn("timestamp")
                .setOwnerColumn("owner")
                .build();
    }

    @Bean
    public TokenStore tokenStore(DataSource ds, TokenSchema schema) {
        var cfg = JdbcTokenStoreConfiguration.DEFAULT.schema(schema);
        return new JdbcTokenStore(
                new JdbcTransactionalExecutorProvider(ds),
                new JacksonConverter(),  // 引数なしで Axon 内部 ObjectMapper を使用
                cfg);
    }
}
```

#### 重要な実装ポイント

- **`SpringTransactionManager` は ConnectionProvider 付きコンストラクタで作る**。`ConnectionProvider` が null だと `JdbcTransactionalExecutorProvider.SUPPLIER_KEY` が UnitOfWork に bind されず、`Coordinator.initializeTokenStore()` で `JdbcTokenStore.connectionExecutor()` が「A connection executor must be present in the processing context」で失敗する。
- **Axon の `TransactionManager` 型 Bean は唯一であること**。`SpringComponentRegistry` が `getIfUnique()` で取得するため、複数あると `NoTransactionManager` にフォールバックする。
- **PSEP / Coordinator 側に `transactionManager` を attach する追加 YAML/Java は不要**。`MessagingConfigurationDefaults` の `TransactionalUnitOfWorkFactory` が自動でセットアップする。

**4. Flyway migration**

- `bookingms/src/main/resources/db/migration/V003__create_token_entry.sql`
- `routingms/src/main/resources/db/migration/V002__create_token_entry.sql`

```sql
CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INTEGER      NOT NULL,
    mask           INTEGER      NOT NULL,
    token          BYTEA,
    token_type     VARCHAR(255),
    timestamp      VARCHAR(255),
    owner          VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);
```

**5. `application-local-docker.yml`（bookingms / routingms）**

```yaml
axon:
  axonserver:
    servers: ${AXON_AXONSERVER_SERVERS:localhost:8124}
  # subscribing 上書きを削除（デフォルト pooled-streaming に復帰）
```

**6. `application-local-h2.yml`（bookingms / routingms）**

```yaml
axon:
  axonserver:
    # connector が classpath 常駐するため、local-h2 では autoconfig を明示的に無効化
    enabled: false
  eventhandling:
    processors:
      "[com.example.cargotracker.bookingms.interfaces.events]":
        mode: subscribing  # local-h2 は引き続き in-memory（ADR-0008）
```

**7. `apps/docker-compose.yml` の axonserver service**

Axon Server 2026.0.0 では standalone モードと **DCB (Dynamic Consistency Boundary) 形式の default Context** を明示的に有効化しないと、クライアントの接続で `AXONIQ-1302 (default: not found)` または `AXONIQ-2308 (Operation not supported on non-DCB context)` になる。

```yaml
environment:
  AXONIQ_AXONSERVER_DEVMODE_ENABLED: "true"
  AXONIQ_AXONSERVER_STANDALONE: "true"      # default Context を自動作成
  AXONIQ_AXONSERVER_STANDALONE_DCB: "true"  # Axon Framework 5 が要求する DCB 形式で作成
```

### 検証手順と結果

```bash
# 完全リセット → 起動
CONFIRM_CLEAN=yes gulp local-docker:clean
gulp local-docker:up

# シナリオ A: AxonServer UP で POST → 201
curl -X POST http://localhost:8082/api/v1/bookings -d '...'  # → HTTP 201 (342ms)

# Read Model 投影確認（PooledStreamingEventProcessor 経由）
curl http://localhost:8082/api/v1/bookings  # → bookingId が返る

# シナリオ B: AxonServer DOWN で POST → 失敗
docker stop cargo-axonserver
curl -X POST http://localhost:8082/api/v1/bookings -d '...'  # → HTTP 500 (92ms)
```

起動ログに以下が出力されることを確認:
```
Connected instruction stream for context 'default'. Sending client identification with clientId ...
CommandChannel for context 'default' connected
Registered handler for command 'com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand' in context 'default'
```

### 代替案

#### 案 1: starter は 5.1.0 GA / connector のみ 5.1.0-RC2

当初の案。GA を最大限維持するため connector のみ RC を採用する組み合わせ。実証実験の結果、`axon-messaging:5.1.0 GA` ↔ `axon-server-connector:5.1.0-RC2` で `QueryBusConnector` インタフェースの ABI が一致せず Spring Boot Auto Configuration が `NoClassDefFoundError` で失敗。**却下**（決定として全 Axon RC2 揃えを採用）。

#### 案 2: 5.0.x 安定版へ全面ダウングレード

`axon = "5.0.5"` まで下げれば connector も GA で揃う。しかし ADR-0007 で確立した `@EventSourced` / `EventAppender` 等の API が 5.1 で導入されたものを含み、再検証コストが大きい。**却下**。

#### 案 3: TokenSchema をデフォルト（キャメルケース）のまま採用

`TokenEntry` / `processorName` のテーブル・カラム名は PostgreSQL でダブルクォート必須となり、手動 psql 操作・別ツールからの参照で運用上ハマる。長期的なコストが高い。**却下**。

#### 案 4: subscribing のまま運用継続

connector 不在のままでは subscribing 自体が「Axon Server を経由しない in-memory 同期実行」となり、ADR-0001 の Axon Framework 採用根拠（CQRS / Event Sourcing / 分散イベント配信）が成立しない。**却下**。

## 影響

### ポジティブ

- **`axon.axonserver.servers` 設定が初めて機能する**: 起動ログに `Connected to AxonServer` が現れ、CommandBus・EventStore・EventProcessor すべてが Axon Server 経由になる。
- **CQRS の本来のセマンティクスが復活**: BookCargoCommand 発行が Cargo Aggregate（Axon Server 側 Event Store）に渡り、CargoBookedEvent が PooledStreamingEventProcessor を通じて非同期で Read Model に反映される。
- **Axon Server 停止検知**: コマンド送信が失敗するようになるため、運用環境で Axon Server ダウンを即座に検知できる。テストでも障害シナリオを再現可能。
- **snake_case 命名**: Flyway migration の DDL がそのまま psql で扱える。リファクタリング・他言語クライアントからの参照も自然。

### ネガティブ

- **Axon 系が pre-release**: starter / axon-test / connector のすべてが `5.1.0-RC2`。GA からはわずかに後退（CHANGELOG の追跡が前提で、GA `5.1.0` connector 公開後に揃え直しが必要）。RC2 ↔ GA の ABI 不一致が確認されたため、AxonIQ 側で GA connector が出るまでは RC で統一せざるを得ない。
- **手動 JDBC 構成の責任**: `JdbcAutoConfiguration` / `JdbcTransactionAutoConfiguration` を Jackson 互換性問題で利用できないため、`AxonJdbcConfig` で `ConnectionProvider` / `TransactionManager` / `TokenSchema` / `TokenStore` 4 Bean を手動構成。Axon 側 autoconfig が GA で改善されたら本クラスを削除して autoconfig に戻す検討が必要。
- **Axon Server 2026 の DCB 要件**: standalone 起動時に `STANDALONE_DCB=true` を忘れると `AXONIQ-2308 (Operation not supported on non-DCB context)` で Coordinator が永久リトライする。本番のクラスタ構成時は cluster template で `dcb=true` を指定。
- **routingms の cold start が遅延**: PooledStreamingEventProcessor は起動時に Token Store の検証・claim 取得を行うため、`docker compose up` の起動時間が数秒延びる可能性。
- **テストの再検証**: `springboot-integration-test` プロファイルで `@MockitoBean CommandGateway` を使っているテストには影響しないが、`local-h2` プロファイルは subscribing で動かす ADR-0008 の方針を維持するため、両モード共存が継続。

### 中立

- **既存 ADR-0008 はそのまま有効**: `@EventSourced` Spring stereotype + `@Profile("!springboot-integration-test")` のパターン、`local-h2` での subscribing 維持は本 ADR で変更しない。

## コンプライアンス

### 自動検証

- [x] `./gradlew :bookingms:test :routingms:test` が GREEN
- [x] `gulp local-docker:clean` → `gulp local-docker:up` 後、bookingms / routingms の起動ログに `Connected instruction stream for context 'default'` が出力される
- [x] `gulp local-docker:up` 後、psql で `SELECT * FROM token_entry;` が実行できる
- [x] axonserver 稼働中の `POST /api/v1/bookings` が `201 Created` を返す（実測 342ms）
- [x] axonserver 停止中の `POST /api/v1/bookings` が 500 を返す（in-memory フォールバックしない／実測 92ms）
- [x] `GET /api/v1/bookings` の Read Model に PooledStreamingEventProcessor 経由で投影される

### コードレビュー時のチェックポイント

- 新規マイクロサービスを追加する場合、`build.gradle.kts` に `implementation(libs.axon.server.connector)` を必ず含める
- TokenSchema を変える場合、Flyway migration の DDL も同時に変更する（命名同期）
- `application-*.yml` で `axon.eventhandling.processors.*.mode` を意図的に `subscribing` に上書きするのは `local-h2` プロファイルのみ
- `axon-server-connector` のバージョンを上げる際は connector / starter / axon-test の組み合わせを `axon-bom`（公開され次第）で揃える

## 備考

- 著者: AI Agent（xp-architect / xp-programmer）
- 関連 ADR:
  - [ADR-0001](0001-axon-framework-adoption.md) — Axon Framework 採用
  - [ADR-0007](0007-axon-5-event-sourcing-api.md) — Axon 5.1 Event Sourcing API
  - [ADR-0008](0008-axon-5-spring-boot-integration-pattern.md) — Axon 5 / Spring Boot 4 統合パターン（本 ADR で IT3 課題を解消）
- 参考:
  - [`axon-spring-boot-starter-5.1.0.pom`（connector 不在の一次資料）](https://repo1.maven.org/maven2/org/axonframework/extensions/spring/axon-spring-boot-starter/5.1.0/axon-spring-boot-starter-5.1.0.pom)
  - [`axon-server-connector` Maven metadata（5.1.0 未公開）](https://repo1.maven.org/maven2/org/axonframework/axon-server-connector/maven-metadata.xml)
  - [AxonFramework PR #2269 — `axon-server-connector` の独立化](https://github.com/AxonFramework/AxonFramework/pull/2269)
  - [`TokenSchema.java`（axon-5.1.0 タグ）](https://github.com/AxonIQ/AxonFramework/blob/axon-5.1.0/messaging/src/main/java/org/axonframework/messaging/eventhandling/processing/streaming/token/store/jdbc/TokenSchema.java)
  - [`JdbcAutoConfiguration.java`（axon-5.1.0 タグ）](https://github.com/AxonIQ/AxonFramework/blob/axon-5.1.0/extensions/spring/spring-boot-autoconfigure/src/main/java/org/axonframework/extension/springboot/autoconfig/JdbcAutoConfiguration.java)
