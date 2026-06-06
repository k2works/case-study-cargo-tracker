package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * 入金残額追跡値オブジェクト（IT9 A1.3 / ADR-0020 / US26）。
 *
 * <p>請求総額（{@code totalDue}）と累積入金額（{@code paidSoFar}）を保持し、残額と完全入金判定を提供する。
 * 不変オブジェクト（record）として実装され、{@link #apply(BigDecimal)} で新しいインスタンスを返す。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code totalDue >= 0}</li>
 *   <li>{@code 0 <= paidSoFar <= totalDue}</li>
 * </ul>
 *
 * @param totalDue  請求総額（Invoice.totalAmount と整合）
 * @param paidSoFar 累積入金額
 */
public record BalanceTracker(
        BigDecimal totalDue,
        BigDecimal paidSoFar
) {

    public BalanceTracker {
        if (totalDue == null || totalDue.signum() < 0) {
            throw new IllegalArgumentException("totalDue は 0 以上の値で必須です: " + totalDue);
        }
        if (paidSoFar == null || paidSoFar.signum() < 0) {
            throw new IllegalArgumentException("paidSoFar は 0 以上の値で必須です: " + paidSoFar);
        }
        if (paidSoFar.compareTo(totalDue) > 0) {
            throw new IllegalArgumentException(
                    "paidSoFar が totalDue を超過しています: paidSoFar=" + paidSoFar + ", totalDue=" + totalDue);
        }
    }

    /** 累積入金額 0 で初期化する。 */
    public static BalanceTracker of(BigDecimal totalDue) {
        return new BalanceTracker(totalDue, BigDecimal.ZERO);
    }

    /** 残額（totalDue - paidSoFar）。 */
    public BigDecimal remainingBalance() {
        return totalDue.subtract(paidSoFar);
    }

    /** 完全入金（残額がゼロ）かどうか。 */
    public boolean isFullyPaid() {
        return remainingBalance().signum() == 0;
    }

    /** 部分入金を適用した新しいインスタンスを返す。totalDue 超過は例外。 */
    public BalanceTracker apply(BigDecimal payment) {
        if (payment == null || payment.signum() <= 0) {
            throw new IllegalArgumentException("payment は 0 より大きい値で必須です: " + payment);
        }
        BigDecimal newPaid = paidSoFar.add(payment);
        if (newPaid.compareTo(totalDue) > 0) {
            throw new IllegalArgumentException(
                    "入金額が総額を超過しています: paidSoFar=" + paidSoFar + ", payment=" + payment
                            + ", totalDue=" + totalDue);
        }
        return new BalanceTracker(totalDue, newPaid);
    }

    /** 総額を更新した新しいインスタンスを返す（割引・調整適用時）。 */
    public BalanceTracker withTotalDue(BigDecimal newTotalDue) {
        return new BalanceTracker(newTotalDue, paidSoFar);
    }
}
