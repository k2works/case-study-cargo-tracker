package com.example.billingms.domain.model;

/**
 * 発行した時点で確定した金額（[ADR-027] 決定 4）。
 *
 * <p><strong>係数から計算し直さない。</strong>基準運賃・貨物種別係数・税率を将来変えた
 * とき、計算し直す実装だと<strong>過去に発行した請求書の金額が黙って変わる</strong>。
 * 請求書は荷主へ出す約束であり、出したあとに変わってはならない。
 *
 * <p>根拠（{@link InvoiceCharges}）は「なぜその金額か」を説明するために残す。
 * <strong>金額そのものはこちらが持つ。</strong>
 *
 * @param baseAmount 基本料金
 * @param discountAmount 割引額
 * @param taxAmount 消費税
 * @param totalAmount 合計
 */
public record InvoiceAmounts(Money baseAmount, Money discountAmount, Money taxAmount,
        Money totalAmount) {

    public InvoiceAmounts {
        if (baseAmount == null || discountAmount == null || taxAmount == null
                || totalAmount == null) {
            throw new IllegalArgumentException("金額を指定してください");
        }
    }

    /**
     * 発行の時点で計算する。
     *
     * <p>ここでだけ係数を使う。復元では保存された値をそのまま受け取る。
     */
    public static InvoiceAmounts calculate(InvoiceCharges charges,
            java.util.List<InvoiceLineItem> lineItems) {
        Money subtotal = charges.subtotalBeforeAdjustments();
        for (InvoiceLineItem item : lineItems) {
            subtotal = subtotal.add(item.amount());
        }
        Money tax = charges.taxRate().taxOf(subtotal);
        return new InvoiceAmounts(charges.baseAmount(), charges.discountAmount(), tax,
                subtotal.add(tax));
    }

    /** 税を含まない小計。 */
    public Money subtotal() {
        return totalAmount.subtract(taxAmount);
    }
}
