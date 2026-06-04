package com.example.billingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.billingms.domain.model.BillingStatus.CALCULATED;
import static com.example.billingms.domain.model.BillingStatus.CANCELLED;
import static com.example.billingms.domain.model.BillingStatus.INVOICED;
import static com.example.billingms.domain.model.BillingStatus.OVERDUE;
import static com.example.billingms.domain.model.BillingStatus.PAID;
import static com.example.billingms.domain.model.BillingStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BillingStatus} の状態遷移検証（US21 / US22 / US23 / IT7 タスク 2.1）。
 *
 * <p>iteration_plan-7.md L405-408 の状態遷移マシンと domain-model.md L913-920 を実装する。</p>
 *
 * <pre>
 * 許可遷移:
 *   PENDING → CALCULATED          (CalculateInvoiceCommand 受理時、US21)
 *   CALCULATED → CALCULATED        (ApplyDiscountCommand / AdjustInvoiceCommand、自己ループ可)
 *   CALCULATED → INVOICED          (IssueInvoiceCommand、US23)
 *   CALCULATED → CANCELLED         (CancelInvoiceCommand、IT8 拡張)
 *   INVOICED → PAID                (RecordPaymentCommand、US23)
 *   INVOICED → OVERDUE             (MarkOverdueCommand、Scheduler)
 *   INVOICED → CANCELLED           (CancelInvoiceCommand、IT8 拡張)
 *   OVERDUE → PAID                 (遅延入金、US23)
 *   PAID / CANCELLED → なし        (終端状態)
 * </pre>
 */
class BillingStatusTest {

    @Test
    @DisplayName("PENDING からは CALCULATED への遷移のみ許可")
    void PENDINGからの遷移() {
        assertThat(PENDING.canTransitionTo(CALCULATED)).isTrue();
        assertThat(PENDING.canTransitionTo(INVOICED)).isFalse();
        assertThat(PENDING.canTransitionTo(PAID)).isFalse();
        assertThat(PENDING.canTransitionTo(OVERDUE)).isFalse();
        assertThat(PENDING.canTransitionTo(CANCELLED)).isFalse();
        assertThat(PENDING.canTransitionTo(PENDING)).isFalse();
    }

    @Test
    @DisplayName("CALCULATED からは自身（割引・調整）/ INVOICED / CANCELLED へ遷移可")
    void CALCULATEDからの遷移() {
        assertThat(CALCULATED.canTransitionTo(CALCULATED)).isTrue();
        assertThat(CALCULATED.canTransitionTo(INVOICED)).isTrue();
        assertThat(CALCULATED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CALCULATED.canTransitionTo(PENDING)).isFalse();
        assertThat(CALCULATED.canTransitionTo(PAID)).isFalse();
        assertThat(CALCULATED.canTransitionTo(OVERDUE)).isFalse();
    }

    @Test
    @DisplayName("INVOICED からは PAID / OVERDUE / CANCELLED へ遷移可")
    void INVOICEDからの遷移() {
        assertThat(INVOICED.canTransitionTo(PAID)).isTrue();
        assertThat(INVOICED.canTransitionTo(OVERDUE)).isTrue();
        assertThat(INVOICED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(INVOICED.canTransitionTo(PENDING)).isFalse();
        assertThat(INVOICED.canTransitionTo(CALCULATED)).isFalse();
        assertThat(INVOICED.canTransitionTo(INVOICED)).isFalse();
    }

    @Test
    @DisplayName("OVERDUE からは遅延入金（PAID）への遷移のみ許可")
    void OVERDUEからの遷移() {
        assertThat(OVERDUE.canTransitionTo(PAID)).isTrue();
        assertThat(OVERDUE.canTransitionTo(PENDING)).isFalse();
        assertThat(OVERDUE.canTransitionTo(CALCULATED)).isFalse();
        assertThat(OVERDUE.canTransitionTo(INVOICED)).isFalse();
        assertThat(OVERDUE.canTransitionTo(OVERDUE)).isFalse();
        assertThat(OVERDUE.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("PAID は終端状態（どこにも遷移できない）")
    void PAIDからの遷移は不可() {
        for (BillingStatus to : BillingStatus.values()) {
            assertThat(PAID.canTransitionTo(to))
                    .as("PAID → %s は不可", to)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CANCELLED は終端状態（再発行は新規 Invoice）")
    void CANCELLEDからの遷移は不可() {
        for (BillingStatus to : BillingStatus.values()) {
            assertThat(CANCELLED.canTransitionTo(to))
                    .as("CANCELLED → %s は不可", to)
                    .isFalse();
        }
    }
}
