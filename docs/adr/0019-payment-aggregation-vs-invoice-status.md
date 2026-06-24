# 0019 Payment は Invoice 集約内のステータスとして表現する（別集約化しない）

Billing Context における入金管理を、Invoice 集約内の `paymentStatus` フィールドと `confirmPayment` メソッドで表現し、`Payment` を独立した集約には**しない**。

日付: 2026-06-24

## ステータス

2026-06-24 承認・適用 (IT8 Day 1 必須決定、タスク 0.15)。US23 精算処理 (タスク 2.1-2.10) はこの ADR に従い実装する。

## コンテキスト

IT8 の US23「精算を処理する」着手前に、Billing Context の **Payment（入金）** をどのドメインオブジェクトとして表現するかを決定する必要がある。

### 既存設計と計画案の衝突

| 観点 | 案 A: Payment 独立集約 | 案 B: Invoice 集約内ステータス |
| :--- | :--- | :--- |
| 出典 | `iteration_plan-8.md` 2.1 計画案 | `docs/design/domain-model.md` L921-955 既存設計 |
| 集約境界 | Payment / Invoice の 2 集約 | Invoice 1 集約に paymentStatus + confirmPayment 内包 |
| Payment フィールド | `Payment(paymentId, invoiceId, amount, dueDate, status, paidAt, referenceCode, version)` | Invoice 内: `paymentStatus: PaymentStatus`, `paidAt: Option[Instant]`, `paymentReference: Option[String]` |
| トランザクション境界 | Payment と Invoice の整合性を別 TX で担保 | Invoice 内で完結（単一 TX） |

既存 `domain-model.md` (commit 履歴上 IT5-IT7 で承認済) では Invoice 集約内に `paymentStatus` + `confirmPayment(paidAt)` を持つ案で設計されていた。IT8 計画策定時に「決済機関連携を見据えて Payment を独立集約に」という案が浮上し、validating-iteration-plan で S3-1 / S3-2 / S3-3 の不整合として検出された。

### Payment の業務ライフサイクル（IT8 スコープ）

```
Invoice 確定 (Confirmed) → 支払期日設定 + reference_code 発行
                       ↓
                   Pending  ───(期日超過)──→ Overdue
                       ↓                       ↓
                       ↓ (入金確認)            ↓ (入金確認)
                       ↓                       ↓
                   Confirmed ←─────────────────┘
                       ↓
                       ↓ (BookingStatus → Settled イベント発火)
                       ↓
                   (Refunded は IT9+ で検討)
```

IT8 では「**手動 reference_code 入力による入金確認**」のみをスコープとし、外部決済機関連携（バッチ取込・Webhook）は **US23 受入基準 3 から除外して IT9 申し送り**としている。

## 決定

**案 B（Invoice 集約内 `paymentStatus` + `confirmPayment` メソッド）を採択する。**

### 採択理由

1. **既存設計の継続性**: `domain-model.md` L921-955 で既に承認済み。IT5-IT7 を通じて他の設計ドキュメント（`ui_design.md` 請求書詳細画面、`data-model.md` invoice テーブル）が Invoice 内案を前提に整合している。
2. **強い不変条件結合**: 「Invoice.paymentStatus = Confirmed ⇔ Payment.status = Confirmed」が常に成立すべき業務制約。集約分離すると 2 集約間の整合性維持に別途分散トランザクション（Saga / Outbox）が必要となり、IT8 スコープに対し過剰投資。
3. **1:1 のライフサイクル**: IT8 スコープでは Invoice 1 件あたり Payment 1 件（全額一括払い）が業務上の典型。分割払い・部分払いは現時点で要件外。
4. **YAGNI 原則**: 「将来の決済機関連携 / 分割払い」を見越して集約分離するのは投機的設計。実際にその要件が発生した時点で `Payment` を `Invoice` から切り出すリファクタリングは可能（ADR 0014 Snapshot ADT パターンが移行を支援する）。
5. **シンプルな TX 境界**: `BillingCommandService.confirmPayment(invoiceId, paidAt, referenceCode)` が単一の `DB.localTx` で `Invoice` を読み出し、`invoice.confirmPayment(...)` を適用し、保存するだけで完結する。

### 案 A を却下した理由

- **過剰な分散トランザクション**: 「Payment.confirm → Invoice.paymentStatus 更新」を 2 集約に分けると、必然的に Outbox パターンや eventual consistency 設計が要求され、IT8 (9 SP / 2 週間) で着地困難。
- **集約分離の弱い動機**: 「決済機関連携」も「分割払い」も IT8 スコープ外であり、現時点で集約分離する必然性は低い。
- **既存設計の手戻りコスト**: `data-model.md` の `invoice` テーブル `paid_amount_value` / `paid_at` / `transaction_reference` 列が既に Invoice 内ステータス前提で設計されている。案 A 採択時は `payment` テーブルへの分離と migration 追加が必要となり、V17 で既に作成済みの `payment` テーブルとの整合性調整も発生。

### 案 B 採択時の実装方針

```scala
// domain/model/invoice/Invoice.scala（既存ベース）
final case class Invoice private (
    invoiceId: InvoiceId,
    cargoBookingId: BillingBookingId,
    shipperId: BillingShipperId,
    baseAmount: Money,
    discountRate: DiscountRate,
    finalAmount: Money,
    paymentStatus: PaymentStatus,
    issuedAt: Instant,
    paidAt: Option[Instant],
    paymentReference: Option[String], // IT8 新規（reference_code）
    dueDate: Option[LocalDate],        // IT8 新規（支払期日）
    version: Long
):
  def issuePayment(dueDate: LocalDate, referenceCode: String): Either[DomainError, Invoice] =
    paymentStatus match
      case PaymentStatus.NotIssued => Right(copy(
        paymentStatus = PaymentStatus.Pending,
        dueDate = Some(dueDate),
        paymentReference = Some(referenceCode)
      ))
      case _ => Left(DomainError.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Pending))

  def confirmPayment(paidAt: Instant): Either[DomainError, Invoice] =
    paymentStatus match
      case PaymentStatus.Pending | PaymentStatus.Overdue => Right(copy(
        paymentStatus = PaymentStatus.Confirmed,
        paidAt = Some(paidAt)
      ))
      case _ => Left(DomainError.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Confirmed))

  def markOverdue(now: LocalDate): Either[DomainError, Invoice] =
    (paymentStatus, dueDate) match
      case (PaymentStatus.Pending, Some(due)) if now.isAfter(due) =>
        Right(copy(paymentStatus = PaymentStatus.Overdue))
      case _ => Left(DomainError.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Overdue))

enum PaymentStatus:
  case NotIssued, Pending, Overdue, Confirmed, Refunded
```

- `Payment` 値オブジェクトは作らない。代わりに `Invoice.toPaymentView: PaymentView` 等の Query Service 側 DTO で UI 向けに整形する。
- データモデルは既存 `invoice` テーブルに `due_date DATE NULL` 列を追加する Flyway V23 を IT8 で適用。`payment` テーブル（V17 で作成済）は **未使用テーブル** として IT8 V25 で drop する（V17 当時は案 A を見越した先行作成だったため）。
- ui_design.md L88-91 の請求書詳細画面に「支払欄 (paymentStatus / dueDate / paidAt / referenceCode / [入金確認] ボタン)」を追加。独立した精算画面 (`/billing/payments`) は新設しない。
- US23 タスク 2.1-2.10 の主語は全て「Invoice」となる（計画ドキュメントを 0.15 完了時に確定版へ書き換え）。

## 影響

### iteration_plan-8.md 改訂

- 2.1: 「Payment 集約新設」→「Invoice に `dueDate` `paymentReference` 列追加 + `issuePayment` / `confirmPayment` / `markOverdue` メソッド追加」に確定
- 2.4: `issuePayment(invoiceId, dueDate)` → `Invoice.issuePayment` (BillingCommandService 経由)
- 2.8: 精算管理画面 `/billing/payments/*` → 削除、請求書詳細画面 `/billing/invoices/:id` の支払欄統合に変更
- 各タスクの「ADR 0019 結果次第」二段構え注記を削除し、案 B 採択版に一本化

### domain-model.md / data-model.md 改訂（0.12 で実施）

- domain-model.md L921-955: 既存設計を維持しつつ、`Invoice` に `dueDate: Option[LocalDate]` / `paymentReference: Option[String]` を追加。`PaymentStatus` enum に `NotIssued` / `Overdue` を追加
- data-model.md: invoice テーブルに `due_date DATE NULL` 追加、`payment` テーブル削除（V25）。`paid_amount_value` / `paid_amount_currency` は冗長のため finalAmount 参照で代替し列削除

### Flyway 改訂

| Migration | 当初計画 | 改訂後 |
| :--- | :--- | :--- |
| V23 | corporate_discount_policy 新設 | corporate_discount_policy 新設 **+ invoice.due_date 追加** |
| V24 | payment テーブル拡張 (payment_number / due_date / status / version / updated_at) | **削除（不要）** |
| V25 | （未割当）| **payment テーブル drop** + paid_amount_value / paid_amount_currency 列削除 |

### テスト

- `InvoiceSpec` に `issuePayment` / `confirmPayment` / `markOverdue` の状態遷移テスト 6 ケース追加
- `Payment` 集約用テスト（計画されていた `PaymentSpec`）は作成しない

### 帰結

- **複雑性削減**: 集約 1 つで済むため、TX 境界・整合性検証・Repository の数が半減
- **将来拡張の柔軟性低下**: 分割払い / 決済機関連携が要件化した際は Payment 切り出しリファクタリングが必要。ただし ADR 0014 Snapshot ADT がリファクタを支援するため、技術的負債とまでは言えない
- **US23 着地リスク低減**: IT8 (9 SP / 2 週間) スコープでの完成可能性が向上

## コンプライアンス

- ArchUnit ルール (`Payment` パッケージが存在しないこと): IT8 完了時点で `app/cargotracker/billing/domain/model/payment/` ディレクトリが存在しないことを確認
- `domain-model.md` の Invoice クラス図と実装 `Invoice.scala` のフィールド一致確認（IT8 完了時 0.12 で確認）
- Flyway V23-V25 マイグレーション内容が本 ADR と整合していることを IT8 完了時点で確認

## 備考

- 起票者: AI Agent (IT8 Day 1 必須決定、タスク 0.15)
- 関連 ADR: 0014 (Snapshot ADT)、0015 (Money 統一)、0020 (公開追跡画面例外表示方針)
- 関連 Issue: GitHub #224 [scala/take-1][US23] 精算を処理する
- 改訂履歴は本 ADR に追記し、将来案 A への移行が必要となった際は新規 ADR (e.g., 0030) で superseded を記録する
