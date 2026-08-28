package com.example.billingms.application.internal.commandservices;

import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.billingms.domain.model.CancellationFee;
import com.example.billingms.domain.model.DiscountPolicy;
import com.example.billingms.domain.model.DiscountRate;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;

/**
 * 料金の算出結果（[ADR-027] 決定 3）。
 *
 * <p><strong>保存されない。</strong>算出中の精算書は存在せず、確定操作で初めて発行される。
 * この型は<strong>画面に見せるための計算結果</strong>であって、集約ではない。
 *
 * <p>根拠（{@link TransportCharge}・誤配・キャンセル料の料率）を持つ——
 * 金額だけを返すと、画面が「なぜその金額か」を出せない。
 *
 * @param bookingId 予約番号
 * @param shipperName 荷主の社名
 * @param corporate 法人か
 * @param charge 基本料金の根拠
 * @param discountPolicy 割引方針
 * @param misroute 誤配の記録。無ければ {@code null}
 * @param cancellationFee キャンセル料。無ければ {@code null}
 * @param taxRate 税率
 */
public record ChargeCalculation(
        String bookingId,
        String shipperName,
        boolean corporate,
        TransportCharge charge,
        DiscountPolicy discountPolicy,
        BillableCargoSnapshot.Misroute misroute,
        CancellationFee cancellationFee,
        TaxRate taxRate) {

    public Money baseAmount() {
        return charge.baseAmount();
    }

    /** 割引率。**割引が無ければ {@code null}**——0% と契約なしを区別する（[ADR-012]）。 */
    public DiscountRate discountRate() {
        return discountPolicy.rate();
    }

    public Money discountAmount() {
        return discountPolicy.discountOf(baseAmount());
    }

    /** 調整を入れる前の小計。 */
    public Money subtotal() {
        Money amount = baseAmount().subtract(discountAmount());
        return cancellationFee == null ? amount : amount.add(cancellationFee.amount());
    }

    public Money taxAmount() {
        return taxRate.taxOf(subtotal());
    }

    public Money totalAmount() {
        return subtotal().add(taxAmount());
    }
}
