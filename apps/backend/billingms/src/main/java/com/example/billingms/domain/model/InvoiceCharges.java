package com.example.billingms.domain.model;

/**
 * 精算書の金額を決める材料（[ADR-027] 決定 1・決定 6・決定 8）。
 *
 * <p><strong>4 つはいつも揃って動く。</strong>基本料金の根拠が変われば割引額も税額も
 * 変わり、キャンセル料は基本料金から算定される。ばらばらに渡すと、呼び出し側が
 * 「どれとどれが揃っていなければならないか」を知ることになる。
 *
 * <p>まとめたもう 1 つの理由は、{@link Invoice} の生成が 11 引数になっていたことである
 * ——永続化された行からの復元（テーブルの列数がそのまま現れる）とは違い、発行は業務の
 * 操作であり、読めない引数列は設計の問題である。
 *
 * @param charge 基本料金の根拠（区間数・重量・貨物種別）
 * @param discountPolicy 割引方針。<strong>未設定は 0% ではない</strong>（[ADR-012]）
 * @param cancellationFee キャンセル料。キャンセルでなければ {@code null}
 * @param taxRate 税率
 */
public record InvoiceCharges(TransportCharge charge, DiscountPolicy discountPolicy,
        CancellationFee cancellationFee, TaxRate taxRate) {

    public InvoiceCharges {
        if (charge == null) {
            throw new IllegalArgumentException("基本料金の根拠を指定してください");
        }
        if (discountPolicy == null) {
            throw new IllegalArgumentException("割引方針を指定してください");
        }
        if (taxRate == null) {
            throw new IllegalArgumentException("税率を指定してください");
        }
    }

    /** キャンセルを伴わない請求。 */
    public static InvoiceCharges of(TransportCharge charge, DiscountPolicy discountPolicy,
            TaxRate taxRate) {
        return new InvoiceCharges(charge, discountPolicy, null, taxRate);
    }

    public Money baseAmount() {
        return charge.baseAmount();
    }

    public Money discountAmount() {
        return discountPolicy.discountOf(baseAmount());
    }

    /** 割引率。<strong>割引が無ければ {@code null}</strong>——0% と契約なしを区別する。 */
    public DiscountRate discountRate() {
        return discountPolicy.rate();
    }

    /** 調整（明細）を入れる前の小計。 */
    public Money subtotalBeforeAdjustments() {
        Money amount = baseAmount().subtract(discountAmount());
        return cancellationFee == null ? amount : amount.add(cancellationFee.amount());
    }
}
