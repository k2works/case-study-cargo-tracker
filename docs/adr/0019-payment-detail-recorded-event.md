# ADR-0019: PaymentDetailRecorded 補完イベントの導入（cross-service 契約と内部詳細の分離）

IT7 review H1 対応で billingms 内部 `PaymentRecordedEvent`（10 引数：`paymentMethod` / `externalReference` を含む）を廃止し、shared `PaymentRecordedEvent`（8 引数：cross-service 最小契約）に統一した。これにより `payment` テーブルへの `payment_method` / `external_reference` 反映が `null` 固定となり、本機能要件（決済方法の記録、決済機関の取引番号トレース）が IT7 段階で欠落している。本 ADR では IT8 で導入する補完イベント `PaymentDetailRecorded` の設計を確定する。

日付: 2026-06-05

## ステータス

提案中（IT8 着手時に確定）

## コンテキスト

### IT7 review H1 修正による影響

[ADR-0012](0012-cross-service-idempotency-and-transactions.md) §2 集約発火型に違反していた `SharedPaymentRecordedEventPublisher`（内部 event → shared event 派生 publisher）を廃止し、`Invoice` 集約が shared event を直接 `apply` するように修正した（commit 657e4a5a）。

これにより以下のトレードオフが発生:

| 項目 | 修正前（IT7 T4.1） | 修正後（IT7 H1 fix） |
|------|-------------------|---------------------|
| 集約発火型整合 | ❌ 違反（二段イベント） | ✅ 整合 |
| shared event payload | 10 引数（method/ref 含む） | 8 引数（最小契約） |
| payment テーブル反映 | ✅ method/ref 反映 | ⚠️ method/ref が null |
| cross-service 契約安定性 | △ 内部詳細が漏洩 | ✅ 必要最小限 |
| webhook 統合の前準備 | △ 既存 event の拡張が必要 | ✅ 別 event で局所化可能 |

shared event の役割は「bookingms が Cargo を SETTLED に遷移させるのに必要な情報」のみ。決済方法・取引番号は billingms 内部の運用情報であり、cross-service 契約に含める必要はないため、修正後の設計は本来あるべき分離と言える。

### IT8 で対応する 2 つの要求

1. **payment_method / external_reference の永続化**: 経理担当者が支払方法（BANK_TRANSFER / CREDIT_CARD / MANUAL）と決済機関の取引番号を S23 詳細画面で確認したい
2. **決済機関 webhook 受信**: Stripe / GMO 等の webhook で部分入金 / 失敗通知を受信して invoice の状態を更新したい（IT8 ADR-0020 で決済機関選定予定）

これらは billingms 内部の関心事であり、cross-service には影響しない。

## 決定

**`PaymentDetailRecorded` 補完イベントを billingms 内部 event として導入する**（IT8 で実装）。

### 方針

1. **配置**: `billingms.domain.events.PaymentDetailRecorded`（shared/events に置かない、cross-service には公開しない）
2. **集約発火**: `Invoice` 集約の `@CommandHandler(RecordPaymentCommand)` 内で
   - `apply(shared PaymentRecordedEvent)` で cross-service 必須項目を伝搬（既存）
   - `apply(internal PaymentDetailRecorded)` で内部運用情報を補完（新規）
3. **同一トランザクション**: 2 つの apply は同じ command handler 内で連続実行されるため、event store 上では同 sequence で永続化される
4. **冪等性**: 集約識別子（invoiceId）が決定論的なら（review M1 architect の InvoiceIdGenerator 同様）リプレイで同一順序で再 apply されるため、event store レベルで自然に冪等

### イベント定義

```java
package com.example.billingms.domain.events;

/**
 * 入金詳細記録イベント（IT8、shared PaymentRecordedEvent の補完）。
 *
 * <p>billingms 内部の決済方法 / 取引番号を永続化する。cross-service 契約には含めない
 * （shared event は ADR-0012 集約発火型に準拠した最小契約として維持）。</p>
 *
 * <p>投影 EventHandler が payment テーブルの payment_method / external_reference 列を更新する。
 * 受信した shared PaymentRecordedEvent と同じ paymentId で関連付け、後段 UPDATE で値を反映する。</p>
 */
public record PaymentDetailRecorded(
        String invoiceId,
        String paymentId,
        String paymentMethod,    // BANK_TRANSFER / CREDIT_CARD / MANUAL
        String externalReference // 決済機関の取引番号（任意）
) {}
```

### 集約ハンドラの変更

```java
@CommandHandler
public void handle(RecordPaymentCommand command, Clock clock) {
    // 状態遷移検証（既存）
    if (!this.billingStatus.canTransitionTo(BillingStatus.PAID)) { ... }
    if (command.paidAmount().compareTo(this.totalAmount) != 0) { ... }
    if (command.currency() == null || !command.currency().equals(this.currency)) { ... }

    // shared event 発火（cross-service：bookingms → SETTLED 反映）
    AggregateLifecycle.apply(new com.example.shared.events.PaymentRecordedEvent(
            this.invoiceId, command.paymentId(), this.bookingId, this.shipperId,
            command.paidAmount(), command.currency(),
            command.paidAt(), LocalDateTime.now(clock)
    ));

    // 内部 event 発火（billingms 内部：payment テーブルの method/ref 補完）
    if (command.paymentMethod() != null || command.externalReference() != null) {
        AggregateLifecycle.apply(new PaymentDetailRecorded(
                this.invoiceId, command.paymentId(),
                command.paymentMethod(), command.externalReference()
        ));
    }
}
```

### 投影 EventHandler の変更

```java
// InvoiceProjection に apply(PaymentDetailRecorded) を追加
public void apply(PaymentDetailRecorded event) {
    paymentMapper.updatePaymentDetail(
            event.paymentId(),
            event.paymentMethod(),
            event.externalReference()
    );
}
```

```sql
-- PaymentMapper.xml に updatePaymentDetail 追加
<update id="updatePaymentDetail">
    UPDATE payment
    SET payment_method = #{paymentMethod},
        external_reference = #{externalReference}
    WHERE payment_id = #{paymentId}
</update>
```

### 代替案の評価

| 代替案 | 採用しない理由 |
|--------|----------------|
| **shared PaymentRecordedEvent を 10 引数に戻す** | ADR-0012 cross-service 契約安定性違反。bookingms が知る必要のない情報を契約に含めることになる |
| **payment テーブルに直接 controller から INSERT** | CQRS Read Model 投影方針違反。Command → Event → Projection のフローを迂回する |
| **PaymentDetailRecorded を shared/events に配置** | 内部運用情報を不必要に cross-service に公開する |
| **Upcaster で shared event を拡張** | upcaster は schema 進化用。runtime に optional field を追加する仕組みは過剰 |

### 部分入金対応への発展（IT9 以降）

webhook 統合時に部分入金（paidAmount < totalAmount）対応する場合は、本 ADR の `PaymentDetailRecorded` を拡張するか、別 ADR で `PartialPaymentRecorded` 補完 event を導入する。`RecordPaymentCommand` の完全一致検証も外部化（`@ConfigurationProperties` で `paymentMatchingStrict` フラグ）して切替可能にする。

## 影響

### 適用対象

- **billingms `Invoice` 集約**: `RecordPaymentCommand` ハンドラに `PaymentDetailRecorded` apply 追加
- **billingms `InvoiceProjection`**: `apply(PaymentDetailRecorded)` メソッド追加（OCP 拡張）
- **billingms `PaymentMapper`**: `updatePaymentDetail` SQL 追加

### 受け入れテスト

- `InvoiceAggregateTest`: RecordPayment 受信時に 2 つの event が連続発火することを `expectEvents` で検証
- `InvoiceProjectionTest`: `apply(PaymentDetailRecorded)` で `updatePaymentDetail` が呼ばれることを検証
- 既存 E2E `cross-service.spec.ts` で `payment.payment_method = 'BANK_TRANSFER'` が反映されることを確認

### 既存 ADR との関係

- **ADR-0012 集約発火型**: 2 つの event を同一 command 内で連続 apply するパターンは集約発火型の許容範囲（複数 event の atomic apply）
- **ADR-0015 billingms cross-service**: shared event は最小契約のまま、内部 event で運用情報を補完する設計を本 ADR で確定
- **ADR-0018 NotificationAdapter**: SendGrid に渡すメール本文に決済方法を含める場合、本 ADR の `PaymentDetailRecorded` を購読する `NotificationEventHandler` 拡張で対応可能

## 備考

- 著者: k2works（IT7 review H1 修正の副作用 → IT8 着手時に確定）
- 関連: IT7 commit 657e4a5a（H1 修正で payment_method 制限発生）、IT8 ADR-0020（決済機関選定、予定）
- IT7 では shared event のみで実装、本 ADR で `payment_method` / `external_reference` 復活までの設計道筋を明確化
