package com.example.billingms.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * Invoice 集約のステートマシン（US21 / US22 / US23、domain-model.md L913-920、IT7 計画書 L405-408）。
 *
 * <p>状態遷移の不変条件は本 enum 内に閉じる（Tell, Don't Ask）。集約は
 * {@link #canTransitionTo(BillingStatus)} を呼んで遷移可否を判定し、不可なら
 * {@code IllegalStateException} を投げる。</p>
 *
 * <pre>
 * PENDING → CALCULATED → INVOICED → PAID
 *               │            │
 *               │            └→ OVERDUE → PAID
 *               │
 *               └→ CANCELLED （IT8 拡張）
 * </pre>
 */
public enum BillingStatus {

    /** 集約未生成の仮想初期状態（{@code billingStatus == null} と等価）。CalculateInvoiceCommand 受理可能。 */
    PENDING,

    /** 料金算出済（基本料金確定、割引・調整適用中）。IssueInvoiceCommand で INVOICED へ。 */
    CALCULATED,

    /** 精算書発行済（invoice_number 採番、payment_due 確定）。RecordPaymentCommand で PAID、MarkOverdueCommand で OVERDUE へ。 */
    INVOICED,

    /** 入金済。終端状態。 */
    PAID,

    /** 支払期限超過。RecordPaymentCommand（遅延入金）で PAID へ復帰可。 */
    OVERDUE,

    /** キャンセル済（IT8 拡張）。終端状態、再発行は新規 Invoice として行う。 */
    CANCELLED;

    /**
     * 状態遷移マトリクス。本 enum 外部からは {@link #canTransitionTo(BillingStatus)} で参照する。
     */
    private static final Map<BillingStatus, Set<BillingStatus>> ALLOWED = Map.of(
            PENDING,    Set.of(CALCULATED),
            CALCULATED, Set.of(CALCULATED, INVOICED, CANCELLED),
            INVOICED,   Set.of(PAID, OVERDUE, CANCELLED),
            OVERDUE,    Set.of(PAID),
            PAID,       Set.of(),
            CANCELLED,  Set.of()
    );

    /**
     * 現在の状態から指定された状態への遷移が許可されているかを返す。
     *
     * @param to 遷移先
     * @return 許可されていれば {@code true}
     */
    public boolean canTransitionTo(BillingStatus to) {
        return ALLOWED.get(this).contains(to);
    }
}
