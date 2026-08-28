package com.example.billingms.domain.model.valueobjects;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 発行したあとの請求書に起きたこと（US23）。
 *
 * <p><strong>5 つはいつも揃って動く。</strong>入金を確認すれば支払いの状態が変わり、
 * 取り消せば理由と日時が入る。ばらばらに渡すと、呼び出し側が「どれとどれが揃って
 * いなければならないか」を知ることになる（{@link InvoiceCharges} と同じ理由）。
 *
 * <p><strong>金額はここに入らない。</strong>発行した金額は動かない（[ADR-027] 決定 4）。
 * ここに入るのは、金額を変えずに起きる出来事だけである。
 *
 * @param paymentStatus 支払いの状態
 * @param dueDate 支払期限（発行日 + 30 日）
 * @param payment 入金の記録。<strong>未入金なら {@code null}</strong>
 * @param voidedAt 取り消した日時。取り消していなければ {@code null}
 * @param voidReason 取り消しの理由。<strong>取り消したなら必ず入る</strong>
 */
public record InvoiceLifecycle(PaymentStatus paymentStatus, LocalDate dueDate, Payment payment,
        Instant voidedAt, String voidReason) {

    /** 発行した直後（未入金・入金も取り消しも無い）。 */
    public static InvoiceLifecycle issued(LocalDate dueDate) {
        return new InvoiceLifecycle(PaymentStatus.PENDING, dueDate, null, null, null);
    }

    /** 入金を確認した。**支払期限も取り消しの記録もそのまま持ち越す。** */
    public InvoiceLifecycle withPayment(Payment confirmed) {
        return new InvoiceLifecycle(PaymentStatus.CONFIRMED, dueDate, confirmed, voidedAt,
                voidReason);
    }

    /** 取り消した（赤伝）。**支払いの状態には混ぜない**（[ADR-028] 決定 4）。 */
    public InvoiceLifecycle voided(Instant at, String reason) {
        return new InvoiceLifecycle(paymentStatus, dueDate, payment, at, reason);
    }

    /** 取り消したか。 */
    public boolean isVoided() {
        return voidedAt != null;
    }

    /** 入金済か。 */
    public boolean isPaid() {
        return paymentStatus == PaymentStatus.CONFIRMED;
    }
}
