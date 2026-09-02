package com.example.billingms.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * 入金の記録（US23-3・[ADR-028] 決定 2）。
 *
 * <p><strong>請求書の属性ではない。</strong>請求書に起きた別の出来事であり、
 * {@code invoice} の行を書き換えずに {@code payment} の行として残す
 * ——発行した請求書の金額は動かない（[ADR-027] 決定 4）。
 *
 * <p><strong>決済機関とは連携していない</strong>（受入基準 23-3 は代替で満たす）。
 * 経理担当者が入金日・金額・方法・参照番号を手で入れる。だからこそ
 * <strong>入れた根拠が残る</strong>ことに意味がある——「入金済」だけでは、
 * いつ・いくら・どの振込かを誰も追えない。
 *
 * @param amount 入金額
 * @param paidAt 入金日。<strong>日付である</strong>——通帳に時刻は無い
 * @param method 入金の方法
 * @param transactionReference 参照番号（振込の照会番号など）。無ければ {@code null}
 */
public record Payment(Money amount, LocalDate paidAt, PaymentMethod method,
        String transactionReference) {

    public Payment {
        if (amount == null || amount.amount().signum() <= 0) {
            // **0 円の入金は記録しない。** 相殺で現金が動かない場合も、
            // 消した金額そのものを入れる——0 だと「何を消したか」が残らない
            throw new IllegalArgumentException("入金額は 0 より大きい値で指定してください: " + amount);
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("入金日を指定してください");
        }
        if (method == null) {
            throw new IllegalArgumentException("入金の方法を指定してください");
        }
        if (transactionReference != null && transactionReference.isBlank()) {
            // 空白だけの参照番号は「入れ忘れ」と区別できない
            transactionReference = null;
        }
    }

    public static Payment of(Money amount, LocalDate paidAt, PaymentMethod method,
            String transactionReference) {
        return new Payment(amount, paidAt, method, transactionReference);
    }
}
