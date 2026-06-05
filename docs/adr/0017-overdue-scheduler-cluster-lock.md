# ADR-0017: OverdueScheduler のクラスタ排他制御方針

billingms の `OverdueScheduler`（`@Scheduled` cron、IT7 T4.6）は単一 instance 前提で実装したが、Heroku で `web` dyno を multi-instance に拡張した瞬間に同一時刻で multi-instance が並列発火し、同 Invoice に `MarkOverdueCommand` が複数回送られるリスクがある。集約側は `IllegalState` で冪等スキップするが、通知 EventHandler（`outbound-billing-notification`）が二重通知を送る可能性が残る。本 ADR では IT8 で採用するクラスタ排他制御の方針を確定する。

日付: 2026-06-05

## ステータス

提案中（IT8 着手時に確定）

## コンテキスト

IT7 T4.6 で `OverdueScheduler` を実装した時点では billingms 単一 instance 前提とし、cron `0 0 9 * * * Asia/Tokyo` で毎日 1 回発火する設計とした。billingms は Heroku Eco dyno（1 instance）で運用予定のため当面は問題ないが、以下の状況で multi-instance 化される可能性がある:

1. **本番トラフィック増加**: 業務量増加で Eco → Standard-1X / 2X dyno に昇格、`heroku ps:scale web=2` で水平スケール
2. **無停止デプロイ**: rolling restart 中に旧バージョン + 新バージョンの 2 instance が一時的に併存し、両者が同時刻に scheduler を発火
3. **Heroku の Preboot 機能**: デプロイ時に 2 つの dyno が並列稼働する時間帯（数分）

これらは IT7 時点では対象外だが、IT8 で本番デプロイを行う前にクラスタ排他制御を確定する必要がある。

### 現状の OverdueScheduler 振る舞い

```text
0 0 9 * * * Asia/Tokyo
↓
OverdueScheduler.scheduledRun()
↓
queryService.findOverdueCandidates() → List<InvoiceSummary> 候補
↓
for each candidate:
  commandGateway.sendAndWait(new MarkOverdueCommand(invoiceId))
  → Invoice 集約: INVOICED でない場合は IllegalState（冪等スキップ）
  → InvoiceOverdueEvent 発火
  → InvoiceNotificationEventHandler: notifyOverdue（重複通知リスク）
```

二重発火時の問題:

- 集約レベル: 1 回目で OVERDUE 遷移、2 回目は IllegalState → `CommandExecutionException` で WARN スキップ。Counter `billing.overdue.skipped{reason=exec_failure}` が増えるが致命的ではない
- 通知レベル: 1 回目と 2 回目で `outbound-billing-notification` プロセッサが両方とも `InvoiceOverdueEvent` を購読する場合がある（subscribing モードでは 1 instance だけ、tracking モードでは tokenStore で排他）

## 決定

**ShedLock + JDBC を採用する**（IT8 で実装）。

### 方針

1. **ShedLock 6.x**（Spring Framework 7 / Spring Boot 4 対応版、Maven Central 最新 6.6.0）を導入し、`@SchedulerLock` アノテーションを `OverdueScheduler.scheduledRun` に付与
2. **LockProvider** は `JdbcTemplateLockProvider` を使用し、既存 `billing_read_db` 内に `shedlock` テーブルを Flyway V3 で作成
3. **lock 名**: `billing-overdue-scheduler`（サービス + scheduler 名で一意）
4. **lockAtMostFor / lockAtLeastFor**: cron 間隔の 80% / 20% を目安（24h 周期なら 19h / 5h）

### 代替案の評価

| 代替案 | 採用しない理由 |
|--------|----------------|
| **Quartz Cluster Mode** | DB スキーマが重く、Spring Boot Starter の依存も大きい。Quartz の cron も `@Scheduled` と表記が異なり学習コスト発生 |
| **Heroku 単一 dyno 制約**（手動運用） | 運用ドキュメントへの依存。`heroku ps:scale web=1` を強制し、デプロイ時の Preboot を OFF にする必要があるが、Eco dyno は Preboot 非対応で問題は当面起きない。だがデプロイ時の rolling restart で短時間 2 instance が並走するリスクは残る |
| **Redis 分散ロック**（Redisson 等） | Redis Add-on（$15/月以上）の運用コスト。billingms 用途にはオーバースペック |
| **Axon Saga + DeadlineManager** | Saga は 1 集約のライフサイクル管理向け。日次バッチには不適 |

### 実装計画（IT8）

```kotlin
// build.gradle
implementation libs.shedlock.spring
implementation libs.shedlock.provider.jdbc.template
```

```java
@Component
public class OverdueScheduler {
    @Scheduled(cron = "${billing.overdue.cron}", zone = "${billing.overdue.zone}")
    @SchedulerLock(name = "billing-overdue-scheduler",
                   lockAtMostFor = "PT19H",
                   lockAtLeastFor = "PT5H")
    public void scheduledRun() {
        runOverdueDetection();
    }
}
```

```sql
-- Flyway V3__create_shedlock_table.sql
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

## 影響

### 適用対象

- **billingms `OverdueScheduler`**: IT8 で `@SchedulerLock` 追加
- **その他 `@Scheduled`** が今後追加される場合: 本 ADR に基づき同じ `JdbcTemplateLockProvider` を流用

### 受け入れテスト

- ShedLock スパイ実装で「2 instance 並列発火時に 1 instance のみが処理する」ことを統合テストで検証
- `runOverdueDetection` 直接呼び出しのユニットテストは ShedLock 非介入で従来通り

### 既存 ADR との関係

- **ADR-0012 cross-service 冪等性**: 本 ADR は scheduler レベルの冪等性。ADR-0012 の集約発火型・フラグ列ガードと相補的
- **ADR-0015 billingms cross-service**: billingms 内部処理の運用拡張として本 ADR を位置付け

## 備考

- 著者: k2works（IT7 review 中 持ち越し → IT8 着手時に確定）
- 関連: IT7 review M-OverdueScheduler-cluster-lock
- IT7 では Micrometer counter のみ実装済み（commit c3c2fe0e）。IT8 で ShedLock 統合
