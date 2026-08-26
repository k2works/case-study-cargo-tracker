package com.example.billingms.domain.model;

/**
 * 支払いの状態（[ADR-027] 決定 3）。
 *
 * <p><strong>ここに「金額を確定したか」を混ぜない。</strong>混ぜると {@code CONFIRMED} が
 * 「支払い確認済み」と「金額確定済み」の 2 つの意味を持ち、US23 で支払いを扱う段になって
 * 初めて破綻する——そのときには請求書がすでに発行されている。
 *
 * <p><strong>本 IT で起こす遷移は「発行（{@code PENDING}）」の 1 本だけである。</strong>
 * 残る 3 本は US23（IT12）。それでも 4 値すべて宣言するのは、扱う場所すべてを回る検査を
 * 置くためである（IT10 Try 3）。
 */
public enum PaymentStatus {

    /** 未入金。**発行した時点の状態**。 */
    PENDING("未入金"),

    /** 入金済（US23）。 */
    CONFIRMED("入金済"),

    /** 支払期限を超過（US23）。 */
    OVERDUE("支払期限超過"),

    /** 返金済（US23）。 */
    REFUNDED("返金済");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    /** 画面に出す名前。**英字のまま出すと、経理担当者は状態を読めない。** */
    public String label() {
        return label;
    }
}
