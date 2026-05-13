# ADR-0007 Axon Framework 5.1 の Event Sourcing 採用と新アノテーション API への対応

bookingms / routingms における Aggregate を Axon Framework 5.1 の Event Sourcing で実装する方針と、Axon 4 系から大幅刷新された API パターンへの対応指針を確定する。

日付: 2026-05-13

## ステータス

承認済み

## コンテキスト

- IT1 では `Shipper` を Event Sourcing で実装する計画だったが、工数超過リスクから CRUD（MyBatis）に切替えた経緯がある（[IT1 ふりかえり P2](../development/retrospective-1.md)）
- IT2 では `Cargo`（bookingms）と `Voyage`（routingms）を本格的に Event Sourcing で実装するため、Day 1 にタイムボックス 4h のスパイクを実施した
- 参考実装である Practical DDD in Enterprise Java（Chapter 6）は Axon Framework **4.2 系** ベースであり、本プロジェクトは Axon **5.1.0** を採用する
- 本プロジェクトの `tech_stack.md` / `architecture_backend.md` は当初 Axon 5 でも 4 系のアノテーション（`@Aggregate` / `@AggregateIdentifier` / `@CommandHandler` / `@EventSourcingHandler` / `AggregateLifecycle.apply()`）が維持されていることを前提として記載されていた

### スパイクで判明した技術的事実

1. **依存解決は成功**: `org.axonframework.extensions.spring:axon-spring-boot-starter:5.1.0` + `org.axonframework:axon-test:5.1.0` は Spring Boot 4.0.6 / Java 25 上で問題なく解決される。
2. **4 系のアノテーション API は全削除**: Axon 5.1 では 4 系の以下のパッケージ・クラスが **存在しない**:

     - `org.axonframework.spring.stereotype.Aggregate`（`@Aggregate`）
     - `org.axonframework.modelling.command.AggregateIdentifier`（`@AggregateIdentifier`）
     - `org.axonframework.modelling.command.TargetAggregateIdentifier`（`@TargetAggregateIdentifier`）
     - `org.axonframework.modelling.command.AggregateLifecycle`（`apply()` メソッド）
     - `org.axonframework.test.aggregate.AggregateTestFixture`
     - `org.axonframework.commandhandling.annotation.CommandHandler`（5 系のパッケージは異なる）

3. **5.1 では「Entity」モデルに刷新**: Aggregate という用語は API レベルでは「Entity」に置き換わり、以下の新 API が提供される（`axon-modelling-5.1.0.jar` / `axon-eventsourcing-5.1.0.jar` から確認）:

     - `org.axonframework.eventsourcing.annotation.EventSourcedEntity`
     - `org.axonframework.eventsourcing.annotation.EventSourcingHandler`（維持）
     - `org.axonframework.eventsourcing.annotation.reflection.EntityCreator`（Aggregate コンストラクタ宣言）
     - `org.axonframework.eventsourcing.annotation.reflection.InjectEntityId`
     - `org.axonframework.modelling.annotation.TargetEntityId`
     - `org.axonframework.modelling.annotation.InjectEntity`
     - `org.axonframework.modelling.entity.EntityCommandHandlingComponent`
     - `org.axonframework.eventsourcing.configuration.EventSourcingConfigurer`（機能ベース設定 API）

4. **テスト API も変更**: `AggregateTestFixture` は Axon Test 5.1 には存在しない（`axon-test-5.1.0.jar` の確認）。代替の Given-When-Then API は別途調査が必要。

### IT2 計画書および設計ドキュメントへの影響

| ドキュメント | 影響内容 |
|---|---|
| `iteration_plan-2.md` | `Cargo` Aggregate / `Voyage` Aggregate のタスク説明で 4 系 API（`@Aggregate` 等）を前提に書いていたが、5.1 系 API に置き換える必要がある |
| `architecture_backend.md` | `@Aggregate` / `@AggregateIdentifier` / `@CommandHandler` を用いた実装例（Cargo Aggregate コード例）が 4 系の参考実装ベース。5.1 系の API パターンに更新が必要 |
| `test_strategy.md` | `AggregateTestFixture` を使う前提でテスト例を記載しているが、5.1 系の Given-When-Then 代替 API に置き換える必要がある |
| `tech_stack.md` | 「Annotation 中心 API から **機能ベース API（Configurer / Component Registry）** への移行が推奨される領域あり」と既に注記済み（行 24）。実態の確認が今回完了 |

## 決定

**Axon Framework 5.1 の新アノテーション API を採用し、参考実装（Axon 4 系）からの読み替え方針を確立する。**

### 採用する 5.1 系 API パターン（IT2 Day 2 追加検証で確定）

公式マイグレーションガイドと SaaSForge ブログのサンプルから、Axon 5.1 の Event Sourcing Entity 実装パターンが確定した。以下が本プロジェクトで採用する標準パターンである。

```java
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.modelling.annotation.TargetEntityId;

@EventSourcedEntity(tagKey = "bookingId")
public class Cargo {

    private String bookingId;
    private String shipperId;
    // ...

    @EntityCreator
    protected Cargo() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
        // コレクション型のフィールドは必ずここで初期化する
        // （Axon はリフレクションで生成するため、Lombok Builder を通らない）。
    }

    // Aggregate 作成系の Command は static メソッドとして実装する
    @CommandHandler
    public static String book(BookCargoCommand cmd, EventAppender appender) {
        // バリデーション
        if (cmd.shipperId() == null || cmd.shipperId().isBlank()) {
            throw new IllegalArgumentException("shipperId は必須です");
        }
        // イベント発行（EventAppender 経由、AggregateLifecycle.apply() の代替）
        appender.append(new CargoBookedEvent(
                cmd.bookingId(), cmd.shipperId(), cmd.originUnLocode(), cmd.destinationUnLocode()));
        return cmd.bookingId();
    }

    // Aggregate 更新系の Command はインスタンスメソッドとして実装する
    @CommandHandler
    public void changeDestination(ChangeDestinationCommand cmd, EventAppender appender) {
        appender.append(new CargoDestinationChangedEvent(this.bookingId, cmd.newDestination()));
    }

    // イベント再生で状態を復元する
    @EventSourcingHandler
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
    }

    @EventSourcingHandler
    public void on(CargoDestinationChangedEvent event) {
        // 状態更新
    }
}

public record BookCargoCommand(
        @TargetEntityId String bookingId,
        String shipperId,
        String originUnLocode,
        String destinationUnLocode) {
}
```

### テスト API（`AxonTestFixture`）

`AggregateTestFixture` は **`AxonTestFixture`** に置き換えられた。Given-When-Then のチェーン形式も大幅に変更されている。

```java
import org.axonframework.test.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CargoTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = AxonTestFixture.with(Cargo.class);
    }

    @AfterEach
    void tearDown() {
        fixture.stop(); // ★ リソースリーク防止のため必須
    }

    @Test
    void 予約登録イベントが発行される() {
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand("B-001", "S-001", "JPYOK", "USLAX"))
                .then().success().events(
                        new CargoBookedEvent("B-001", "S-001", "JPYOK", "USLAX"));
    }

    @Test
    void 既存予約の仕向地を変更できる() {
        fixture.given().events(new CargoBookedEvent("B-001", "S-001", "JPYOK", "USLAX"))
                .when().command(new ChangeDestinationCommand("B-001", "USNYC"))
                .then().success().events(
                        new CargoDestinationChangedEvent("B-001", "USNYC"));
    }
}
```

> **注**: `@EventSourced(idType = String.class)` も併用可能（SaaSForge サンプル）。`@EventSourcedEntity(tagKey = "bookingId")` の `tagKey` は **DCB（Dynamic Consistency Boundaries）** のためのイベントメタデータキーで、同一の `bookingId` を持つイベント列を Aggregate として識別する。本プロジェクトでは `tagKey` を必須としつつ、`@EventSourced(idType=)` の併用要否は実装時に検証する。

### 4 系 → 5.1 系 API マッピング表（確定版）

| Axon 4 系 | Axon 5.1 系 | 備考 |
|---|---|---|
| `@org.axonframework.spring.stereotype.Aggregate` | `@org.axonframework.eventsourcing.annotation.EventSourcedEntity(tagKey = "...")` | Spring stereotype ではなく Event Sourcing 専用。`tagKey` で DCB のイベント識別 |
| `@AggregateIdentifier`（フィールド上） | **不要**（`tagKey` でイベント側の識別子を指定するため、フィールドアノテーションは省略可能） | 必要なら `@InjectEntityId` でコンストラクタ引数経由で受け取る |
| `@TargetAggregateIdentifier`（Command 上） | `@org.axonframework.modelling.annotation.TargetEntityId` | Command 上の識別子マーカー、必須 |
| `@CommandHandler`（4 系のインスタンスメソッド） | `@org.axonframework.commandhandling.annotation.CommandHandler` | アノテーション維持。**作成系は static 推奨**、更新系はインスタンスメソッド |
| `@EventSourcingHandler` | `@org.axonframework.eventsourcing.annotation.EventSourcingHandler` | パッケージ移動のみで維持 |
| `AggregateLifecycle.apply(event)` | `EventAppender appender` パラメータ + `appender.append(event)` | ThreadLocal 廃止、依存性注入で明示。非同期・リアクティブ対応 |
| `AggregateTestFixture<T>` | `org.axonframework.test.AxonTestFixture` | チェーン構造変更: `.given().noPriorActivity()`, `.when().command(...)`, `.then().success().events(...)` |
| （なし） | `@EntityCreator`（コンストラクタ上） | **新規必須**。Axon がリフレクションで Entity を生成するため。コレクション初期化もここ |

### 採用方針

1. **5.1 系 API を採用する**: 上記の確定パターンを bookingms / routingms の全 Aggregate で適用する。
2. **作成系 Command は static メソッド** で記述する（公式パターン）。更新系は通常のインスタンスメソッド。
3. **`EventAppender` を Command Handler の引数で受け取る**: `AggregateLifecycle.apply()` の static 呼び出しは禁止。
4. **`@EntityCreator` を必ず宣言する**: コレクション型フィールド（`List` / `Map` 等）はここで初期化する。
5. **テストは `AxonTestFixture` + `fixture.stop()` をペアで使う**: `@AfterEach` で必ず `stop()` を呼ぶ。Spock 連携も将来検討。
6. **Codex への実装指示は 5.1 系コード全文を渡す**: 自然言語で「Aggregate を作って」と指示すると 4 系 API を出力するリスク高。指示には本 ADR のコードパターン全文を含める（[CodexCLIMCPアプリケーション開発フロー.md](../reference/CodexCLIMCPアプリケーション開発フロー.md) の「コード全文を渡す」原則）。
7. **`@EventSourced(idType=)` 併用要否は実装時検証**: SaaSForge サンプルでは両アノテーションを付与している。JAR では `EventSourcedEntity` のみ確認できたため、実装時に併用の必要性を再評価する。

### 検討した代替案

| 代替案 | 利点 | 欠点 | 採否 |
|---|---|---|---|
| **A. Axon 5.1 新 API を採用**（本決定） | 最新版で長期保守可能、エコシステムの将来性 | 公式情報が少なく学習コスト高、参考実装の読み替え必須 | ✅ 採用 |
| B. Axon Framework 4.10.x にダウングレード | Practical DDD in Enterprise Java の参考実装をそのまま流用可能 | tech_stack.md / architecture_backend.md の大幅修正、Spring Boot 4 との整合性の追加検証、学習用ケーススタディの価値が下がる | ❌ 不採用 |
| C. Event Sourcing を諦め CRUD（MyBatis）で実装 | IT1 と同じパターン、確実に動く | 本ケーススタディの中核学習目標（Event Sourcing + CQRS + Saga）を達成できない、Saga 連携が困難になる | ❌ 不採用 |

## 影響

### IT2 計画書の修正（即時反映）

`iteration_plan-2.md` のタスク 1.3 / 1.4 / 4.1 / 4.2 のコード例を 4 系 → 5.1 系 API に置き換える。具体的な API 確認（要追加検証 3 項目）は IT2 Day 2 で実施し、Codex への指示テンプレートを Day 3 までに整備する。

### 設計ドキュメントの修正（IT2 完了時に反映）

| ドキュメント | 修正内容 |
|---|---|
| `architecture_backend.md` | Aggregate 実装例（行 652-707）を 5.1 系 API に書き換え。マッピング表を「参考実装との差分」注記に追加 |
| `test_strategy.md` | `AggregateTestFixture` 例（行 109-148）を 5.1 系の代替 API に書き換え（Day 2 検証後） |
| `tech_stack.md` | 「Annotation 中心 API から機能ベース API への移行」注記を、本 ADR への参照付きでより具体化 |

### リスク

| リスク | 影響度 | 対策 |
|---|---|---|
| ~~5.1 系 API の公式ドキュメント不足~~ | ~~高~~ | ✅ **解消**（IT2 Day 2 で公式マイグレーションガイド + SaaSForge ブログから具体パターン取得済み） |
| ~~`CommandHandler` の登録方式が機能ベース API のみで、アノテーションが完全廃止されている可能性~~ | ~~中~~ | ✅ **解消**（アノテーション維持を確認。作成系は static、更新系はインスタンスメソッド） |
| ~~Axon Test 5.1 の Given-When-Then 代替 API が不安定~~ | ~~中~~ | ✅ **解消**（`AxonTestFixture` を使用、`fixture.stop()` のリソース解放に注意） |
| Codex が 5.1 系 API を学習データに含まない可能性 | 高 | 指示には本 ADR の 5.1 系コードパターン全文を必ず含める。`/codex:rescue` の使用も検討 |
| `@EventSourced(idType=)` の併用要否が不明 | 低 | 実装時に併用版・単独版の両方で試し、動く方を採用 |
| DCB（`tagKey`）の動作仕様詳細 | 中 | IT2 Day 3 の Cargo Aggregate 実装で実機検証。問題があれば本 ADR を更新 |

### IT2 への波及

- **タスク 0.1 Event Sourcing スパイク**: ✅ 完了（本 ADR 作成）
- **タスク 0.2 ADR-0007 起案**: ✅ 完了
- **タスク 0.8（追加）5.1 系 API 確認**: ✅ 完了（IT2 Day 2 で公式マイグレーションガイドおよび SaaSForge ブログから具体パターンを取得し、本 ADR に反映）
- **Day 3 以降の実装**: 本 ADR の「採用する 5.1 系 API パターン」セクションをそのまま Codex 指示の雛形として使用する

## 関連リソース

- スパイク実施日: 2026-05-13（IT2 Day 1 タイムボックス）
- 追加検証実施日: 2026-05-13（IT2 Day 2 想定タスクを前倒し実施）
- スパイクコード: 一時的に `apps/backend/bookingms/src/test/java/.../spike/` 配下に作成→ 削除済み（コンパイル失敗を確認し本 ADR に学びを集約）
- Axon 5.1 JAR 確認: `axon-modelling-5.1.0.jar` / `axon-eventsourcing-5.1.0.jar` のクラス一覧
- 公式マイグレーションガイド: <https://docs.axoniq.io/axon-framework-reference/5.0/migration/>
- マイグレーション実例: <https://saasforge.cz/blog/axon-framework-4-to-5-migration/>（具体的な before/after コード）
- AxonIQ Discuss（Spock 連携）: <https://discuss.axoniq.io/t/testing-axon-5-aggregates-with-spock-patterns-and-gotchas/6654>
- 参考: [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](0001-axon-framework-adoption.md)
- 参考: [Practical DDD in Enterprise Java Chapter 6](https://www.amazon.com/Practical-DDD-Enterprise-Java/dp/1484272250)（Axon 4.2 系の参考実装、本プロジェクトでは読み替えが必要）
- 影響を受ける IT2 タスク: 1.3（`Cargo` Aggregate）、1.4（Command/Event）、4.1（`Voyage` Aggregate）、4.2（Command/Event）、7.2（PITest）テスト戦略連動
