package com.example.cargotracker.billing.domain.model.valueobjects;

import java.time.Instant;

/**
 * 入金の記録（US23）。
 *
 * <p><strong>一部入金は認めない</strong>（IT14 の設計判断）。入金は
 * 「請求額どおりか、そうでないか」の 2 値として扱う。認めると
 * 「未入金額」「入金の履歴」「一部入金中の督促」が芋づるで要り、
 * <strong>要求元のないものを作ることになる</strong>。
 *
 * <p><strong>金額が違う入金は記録せず拒む。</strong> 差額の扱いは業務であり
 * （過入金は返金、不足は再請求）、システムが黙って受けると帳簿と合わなくなる。
 * 拒むのは {@code Invoice} の仕事である（請求額を知っているのは請求書である）。
 *
 * @param paidAmount           入金額
 * @param paidAt               入金日時
 * @param method               支払方法
 * @param transactionReference 取引の参照番号（振込明細・決済番号）。
 *                             <strong>無い入金もある</strong>（窓口振込など）
 */
public record Payment(
        Money paidAmount, Instant paidAt, PaymentMethod method, String transactionReference) {

    public Payment {
        if (paidAmount == null) {
            throw new IllegalArgumentException("入金額は必須です");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("入金日時は必須です");
        }
        if (method == null) {
            throw new IllegalArgumentException("支払方法は必須です");
        }
        transactionReference = transactionReference == null || transactionReference.isBlank()
                ? null : transactionReference.strip();
    }

    /** 参照番号があるか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean hasReference() {
        return transactionReference != null;
    }
}
