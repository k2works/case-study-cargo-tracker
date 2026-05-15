# ADR-0008: Axon Framework 5.1 を Spring Boot 4 に統合する具体パターン

`@EventSourced` (Spring stereotype) と `subscribing` Event Processor を組み合わせた Aggregate 登録パターンを採用し、bootRun / bootJar / 統合テストの 3 環境で一貫して動作させる。

日付: 2026-05-15

## ステータス

承認済み

## コンテキスト

ADR-0007 で Axon Framework 5.1 の Event Sourcing API（`@EventSourcedEntity` / `@EntityCreator` / `static @CommandHandler` / `EventAppender`）採用方針を確定したが、IT2 で `Cargo` Aggregate（bookingms）と `Voyage` Aggregate（routingms）を実装した結果、bootJar / bootRun 環境では **Aggregate の Command Handler が Axon CommandBus に登録されない** 既知バグが発覚した。

### 経緯

1. **統合テストでは動作していた死角**: `BookingControllerIntegrationTest` 等は `@MockitoBean CommandGateway` で CommandGateway 自体をモック化しており、実際に Aggregate が CommandBus へ subscribe されているかを検証できていなかった。
2. **E2E で発覚**: Playwright E2E で実 bootJar に対し `POST /api/v1/bookings` を実行した際、`org.axonframework.messaging.commandhandling.NoHandlerForCommandException: No handler was subscribed for command [BookCargoCommand]` が発生して 500 エラーとなり、データ取得不能になった。
3. **`local-h2` プロファイルの Event Processor 失敗**: コミット `71ec0f71` で `CargoProjectionsEventHandler` / `VoyageProjectionsEventHandler` を `@Profile("!springboot-integration-test & !local-h2")` で除外していたが、これにより local-h2 では Read Model が更新されず一覧画面に表示されない設計上の制約があった。
4. **ADR-0007 で残課題として記載**: 「`@EventSourced(idType=)` の併用要否が不明」というリスクが ADR-0007 のリスク欄に明記されており、本 ADR でその検証結果を確定する。

### 試行した 3 パターン

| 試行 | Cargo / Voyage の宣言 | 結果 |
|------|---------------------|------|
| A | `@EventSourcedEntity(tagKey="bookingId")` のみ | `NoHandlerForCommandException`（CommandBus 未登録） |
| B | A + `@Configuration` で `@Bean EventSourcedEntityModule.autodetected(...)` | `RepositoryAlreadyRegisteredException`（二重登録） |
| C | `@EventSourced(idType=String.class, tagKey="bookingId")` のみ | `DuplicateCommandHandlerSubscriptionException`（テスト時のみ） |

`@EventSourced` は `org.axonframework.extension.spring.stereotype.EventSourced` の Spring stereotype で、メタアノテーションとして `@Component(Scope=prototype)` と `@AliasFor(EventSourcedEntity)` を持つ。Spring Boot Auto Configuration の `SpringComponentRegistry.scanForModules()` が `beanFactory.getBeansOfType(Module.class)` で検出する仕組みのため、`@EventSourced` Bean だけで 1 経路の登録が完結する。

### 発生していた二重登録の原因

パターン C 単独でテスト実行時に Duplicate が発生したのは、`@SpringBootTest` の context cache が複数 test class（`PingControllerIntegrationTest` / `ShipperControllerIntegrationTest` 等）で同じ context を再起動する際に Entity Module が再 subscribe されるため。本番（bootJar 単一起動）では発生しない。

### local-h2 で Projection が動かない問題

`PooledStreamingEventProcessor` は Axon Server の Event Store に接続して `TokenEntry` テーブルを読むため、Axon Server 未起動の local-h2 では起動できない。代替として `SubscribingEventProcessor`（同一 JVM 内の EventBus 購読）を使えば、Command 発行と同じスレッドで Projection が即時実行され Axon Server 不要となる。

## 決定

**`@EventSourced` (Spring stereotype) を使用し、テスト時のみ `@Profile("!springboot-integration-test")` で Bean 化を抑制する。Event Processor は `local-h2` で `subscribing` モードに切替え、Projection Handler の Profile 制約を緩和する。**

### 変更箇所

**1. Aggregate クラス**（`Cargo` / `Voyage`）

```java
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.springframework.context.annotation.Profile;

@EventSourced(idType = String.class, tagKey = "bookingId")
@Profile("!springboot-integration-test")
public final class Cargo {
    @EntityCreator public Cargo() {}
    @CommandHandler
    public static String book(BookCargoCommand cmd, EventAppender appender) {
        appender.append(new CargoBookedEvent(...));
        return cmd.bookingId();
    }
    @EventSourcingHandler public void on(CargoBookedEvent e) { ... }
}
```

- `@EventSourced(idType, tagKey)` で Spring stereotype として 1 経路だけ登録
- `@Profile("!springboot-integration-test")` でテスト時の context 再起動による二重登録を回避

**2. `application-local-h2.yml`**

```yaml
axon:
  eventhandling:
    processors:
      # processor 名は @ProcessingGroup 未指定時にパッケージ名がデフォルトとなる
      "[com.example.cargotracker.bookingms.interfaces.events]":
        mode: subscribing
```

- Spring Boot の Map binding でドット入りキーは `[...]` 記法でクォート
- `subscribing` モードは同一 JVM 内 EventBus 購読のため Axon Server 不要

**3. Projection Handler の `@Profile`**

```java
@Component
@Profile("!springboot-integration-test")  // 旧: "!springboot-integration-test & !local-h2"
public class CargoProjectionsEventHandler {
```

- `local-h2` を除外条件から外し、`subscribing` モードで Projection を有効化

### 代替案

#### 案 1: `@EventSourcedEntity` 単独 + 明示的 `@Bean EventSourcedEntityModule.autodetected(...)`

`@EventSourcedEntity` (axon-eventsourcing) は Spring stereotype を持たないため Spring Boot Auto Config の component scan に拾われない。明示 Bean で Entity Module を登録する案を試したが、**Axon 内部の別経路が同じクラスを Module 化** し `RepositoryAlreadyRegisteredException` が発生。**却下**。

#### 案 2: `EventSourcingConfigurer.registerEntity()` を明示呼び出し

機能ベース API である `EventSourcingConfigurer` を `@Configuration` で呼ぶ案。Spring Boot Auto Config と並行動作させる方法が公式ドキュメントに記載されておらず、`@Bean EventSourcedEntityModule` と同様の二重登録問題が発生する見込み。**却下**。

#### 案 3: POST 機能を IT3 に持ち越し（POST 不可のまま運用）

E2E は GET 系のみ動作確認とし、Aggregate 登録問題は IT3 で解決する案。**却下**：IT2 受入条件「`POST /api/v1/bookings` で予約を登録できる」を達成できない。

#### 案 4: `local-h2` で `PooledStreamingEventProcessor` のまま Projection を無効化

71ec0f71 のコミット時点での選択肢を維持する案。local-h2 で Projection が動かないため E2E で一覧表示を検証できない。**却下**。

## 影響

### ポジティブ

- **bootJar / bootRun の実環境で Aggregate が正常動作**: `POST /api/v1/bookings` / `POST /api/v1/voyages` が `201 Created` を返し、`cargo_summary` / `voyage` Read Model にも反映される。
- **E2E テスト 3 シナリオが全て GREEN**: `login-shipper` / `login-booking` / `login-voyage` が 10.5 秒で完走。Aggregate 登録から Projection 反映までの全ループが動作確認済み。
- **テスト二重登録の回避**: `@Profile("!springboot-integration-test")` により `@SpringBootTest` の context 再起動による Duplicate を抑制。既存統合テストの `@MockitoBean CommandGateway` パターンも維持できる。
- **local-h2 での Projection 動作**: Axon Server 未起動環境でも `SubscribingEventProcessor` で Projection が動くため、`bootRun` + DevTools での TDD ループが完全に閉じる。

### ネガティブ

- **Spring 依存の局所化が後退**: 当初 ADR-0007 では Spring 非依存の `@EventSourcedEntity` を採用していたが、本 ADR で Spring stereotype である `@EventSourced` に置き換えるため、Aggregate クラスが Spring に依存することになる。ADR-0007 のドメイン純粋性の方針からは若干後退するが、Spring Boot Auto Configuration との統合性を優先した。
- **テスト用 `@Profile` の特殊性**: `@SpringBootTest` 時に `springboot-integration-test` プロファイルを `@ActiveProfiles` で明示的に有効化する必要がある（既存テストには既に付与済み）。新規統合テスト追加時のチェックリストに含める必要がある。
- **Subscribing モードの制約**: `subscribing` は同一 JVM 内のみで動作する。`local-docker` / `prod` 等の Axon Server 接続環境では `pooled-streaming` が引き続き使われるため、本番ではマルチノードでの Projection 並列化や at-least-once 保証が依然として有効。
- **processor 名がパッケージ依存**: パッケージ名 `com.example.cargotracker.bookingms.interfaces.events` がそのまま processor 名になるため、リファクタリング時に application-local-h2.yml の更新が必要。

## コンプライアンス

以下の検証で本決定が正しく実装されていることを確認する。

### 自動検証

- [x] `./gradlew :bookingms:test :routingms:test :authms:test` が GREEN（BUILD SUCCESSFUL）
- [x] `npm run test:e2e`（Playwright）が 3 シナリオ全 GREEN
- [x] bootJar 起動した bookingms に `POST /api/v1/bookings` で 201 が返る
- [x] bookingms 起動後 `GET /api/v1/bookings` で投影された予約が返る（Projection 動作確認）
- [x] routingms 側も同様に POST / GET / Projection が動作

### コードレビュー時のチェックポイント

- `@EventSourced` を持つクラスには必ず `@Profile("!springboot-integration-test")` を付与する
- 新規 Aggregate を追加する場合、application-local-h2.yml の `axon.eventhandling.processors` に対応する Event Handler のパッケージ名キーを追加する（`subscribing` モード指定）
- `@EventSourcedEntity` を使わず、必ず `@EventSourced` を使用する
- Spring Boot 統合テストでは `@ActiveProfiles({"local-h2", "springboot-integration-test"})` を必ず指定する

### 起動ログでの確認方法

bootRun 起動時に以下のログが出れば正常：

```
[restartedMain] Devtools property defaults active!
[restartedMain] Tomcat started on port 8082
```

`NoHandlerForCommandException` / `DuplicateCommandHandlerSubscriptionException` / `RepositoryAlreadyRegisteredException` が起動ログに出ていないこと。

## 備考

- 著者: AI Agent（xp-architect / xp-programmer）
- 関連コミット: 本 ADR と同時にコミット予定（IT2 中間進捗で発覚した Axon 統合問題の修正）
- 関連 ADR:
  - [ADR-0001](0001-axon-framework-adoption.md) — Axon Framework 5 採用
  - [ADR-0004](0004-microservice-decomposition.md) — マイクロサービス分割
  - [ADR-0007](0007-axon-5-event-sourcing-api.md) — Axon 5.1 Event Sourcing API 採用方針（本 ADR の前提となる広い決定）
- 参考:
  - [Axon Framework Reference Guide 5.0 – Spring Boot Integration](https://docs.axoniq.io/axon-framework-reference/5.0/spring-boot-integration/)
  - [Axon Framework Reference Guide 5.0 – Subscribing Event Processor](https://docs.axoniq.io/axon-framework-reference/5.0/events/event-processors/subscribing/)
  - [SaaSForge – Migrating from Axon Framework 4 to 5](https://saasforge.cz/blog/axon-framework-4-to-5-migration/)
  - GitHub: `org.axonframework.extension.spring.stereotype.EventSourced`（`axon-spring-5.1.0.jar`）
  - GitHub: `org.axonframework.extension.spring.config.SpringComponentRegistry#scanForModules`（`axon-spring-5.1.0.jar`）
