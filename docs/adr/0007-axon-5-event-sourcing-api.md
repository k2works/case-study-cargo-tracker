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

### 採用する 5.1 系 API パターン

`@EventSourcedEntity` を用いた Event Sourcing Entity（旧称 Aggregate）として実装する。

```java
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.eventsourcing.annotation.reflection.InjectEntityId;
import org.axonframework.modelling.annotation.TargetEntityId;

@EventSourcedEntity
public class Cargo {

    @InjectEntityId
    private String bookingId;
    private String shipperId;
    // ...

    @EntityCreator
    public Cargo() {
        // Axon Framework が Event 再生で呼び出すデフォルトコンストラクタ
    }

    // Command Handler は @EventSourcingHandler ではなく
    // EventSourcingConfigurer で機能ベース API で登録する想定（要追加検証）

    @EventSourcingHandler
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
    }
}

public record BookCargoCommand(
        @TargetEntityId String bookingId,
        String shipperId,
        // ...
) {}
```

### 4 系 → 5.1 系 API マッピング表

| Axon 4 系 | Axon 5.1 系 | 備考 |
|---|---|---|
| `@org.axonframework.spring.stereotype.Aggregate` | `@org.axonframework.eventsourcing.annotation.EventSourcedEntity` | Spring stereotype ではなく Event Sourcing 専用に独立 |
| `@AggregateIdentifier` | `@org.axonframework.eventsourcing.annotation.reflection.InjectEntityId` | Entity 識別子の注入 |
| `@TargetAggregateIdentifier`（Command 上） | `@org.axonframework.modelling.annotation.TargetEntityId` | Command 上の識別子マーカー |
| `@CommandHandler`（4 系の Aggregate メソッド上） | **要追加検証**（`EventSourcingConfigurer` 経由の機能ベース API が主軸の可能性） | 5.1 でアノテーション形式が維持されているかは別途調査 |
| `@EventSourcingHandler` | 同名で維持（パッケージは `eventsourcing.annotation`） | 維持 |
| `AggregateLifecycle.apply(event)` | **要追加検証**（`EventAppender` 等の Bean 注入か機能ベース API） | スパイクでは未検証 |
| `AggregateTestFixture<T>` | **要追加検証**（Axon Test 5.1 の Given-When-Then 代替 API） | テスト戦略への影響大 |

### 採用方針

1. **5.1 系 API を採用する**: Axon 4 系の参考実装をそのまま使うことは不可能。新 API を学習しながら実装する。
2. **IT2 Day 3 以降の本実装着手前に追加検証**: 上表の「要追加検証」項目（`CommandHandler` の登録方式、`AggregateLifecycle.apply()` 代替、テスト Fixture）を IT2 Day 2 までに公式ドキュメント・サンプルで確認する。
3. **Codex への実装指示は新 API のコード全文を渡す**: 自然言語で「Aggregate を作って」と指示すると Codex が 4 系 API を学習データから出力するリスクが高い。指示には 5.1 系のコード全文を含める（[CodexCLIMCPアプリケーション開発フロー.md](../reference/CodexCLIMCPアプリケーション開発フロー.md) の「コード全文を渡す」原則を遵守）。

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
| 5.1 系 API の公式ドキュメント不足 | 高 | AxonIQ 公式 GitHub サンプル・JavaDoc・JAR 内クラス構造を参照。最悪 4.10.x ダウングレード（代替案 B） |
| `CommandHandler` の登録方式が機能ベース API のみで、アノテーションが完全廃止されている可能性 | 中 | IT2 Day 2 で `EventSourcingConfigurer` の使用例を確認。アノテーション併用が可能か検証 |
| Axon Test 5.1 の Given-When-Then 代替 API が不安定 | 中 | テストは統合テスト（Testcontainers + 実 Axon Server）寄りに重心を移す可能性。test_strategy.md の修正で対応 |
| Codex が 5.1 系 API を学習データに含まない可能性 | 高 | 指示には 5.1 系のコード全文を必ず含める。`/codex:rescue` の使用も検討 |

### IT2 への波及

- **タスク 0.1 Event Sourcing スパイク**: 完了（本 ADR 作成）
- **タスク 0.2 ADR-0007 起案**: 完了
- **追加タスク（IT2 Day 2）**: 5.1 系 API の「要追加検証」3 項目を確認し、本 ADR を更新する。タスク番号は 0.8 として `iteration_plan-2.md` に追加する想定（または `users.lock_until` 実装と並行）

## 関連リソース

- スパイク実施日: 2026-05-13（IT2 Day 1 タイムボックス）
- スパイクコード: 一時的に `apps/backend/bookingms/src/test/java/.../spike/` 配下に作成→ 削除済み（コンパイル失敗を確認し本 ADR に学びを集約）
- Axon 5.1 JAR 確認: `axon-modelling-5.1.0.jar` / `axon-eventsourcing-5.1.0.jar` のクラス一覧
- 参考: [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](0001-axon-framework-adoption.md)
- 参考: [Practical DDD in Enterprise Java Chapter 6](https://www.amazon.com/Practical-DDD-Enterprise-Java/dp/1484272250)（Axon 4.2 系の参考実装、本プロジェクトでは読み替えが必要）
- 影響を受ける IT2 タスク: 1.3（`Cargo` Aggregate）、1.4（Command/Event）、4.1（`Voyage` Aggregate）、4.2（Command/Event）、7.2（PITest）テスト戦略連動
