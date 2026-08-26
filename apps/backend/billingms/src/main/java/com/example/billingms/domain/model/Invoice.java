package com.example.billingms.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 精算書（US21・[ADR-027] 決定 3・決定 4）。
 *
 * <p><strong>算出中の精算書は存在しない</strong>（決定 3）。経理担当者が確定操作をした時点で
 * 初めて発行される。下書きを持つと、下書きのまま忘れられた精算書が溜まる——それを見つける
 * 手段をまた作ることになる。
 *
 * <p><strong>発行した精算書の金額は動かない</strong>（決定 4）。請求書は荷主へ出す約束であり、
 * 出したあとに黙って変わると請求の根拠が消える。金額を変える手段をここに置かない。
 * 訂正は US23（IT12）で「取り消して出し直す」形にする。
 *
 * <p><strong>根拠を持ったまま発行する。</strong>基本料金の内訳（区間数・重量・貨物種別）、
 * 割引率、キャンセル料の料率と申請時の状態——金額だけを持つと、あとから
 * 「なぜその金額か」を答えられない。経理担当者は請求の根拠を荷主に説明する。
 */
public final class Invoice {

    private final InvoiceId invoiceId;
    private final BillingBookingId cargoBookingId;
    private final BillingShipperId shipperId;
    private final TransportCharge charge;
    private final DiscountPolicy discountPolicy;
    private final List<InvoiceLineItem> lineItems;
    private final CancellationFee cancellationFee;
    private final TaxRate taxRate;
    private final PaymentStatus paymentStatus;
    private final Instant issuedAt;

    private Invoice(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, TransportCharge charge, DiscountPolicy discountPolicy,
            List<InvoiceLineItem> lineItems, CancellationFee cancellationFee, TaxRate taxRate,
            PaymentStatus paymentStatus, Instant issuedAt) {
        this.invoiceId = invoiceId;
        this.cargoBookingId = cargoBookingId;
        this.shipperId = shipperId;
        this.charge = charge;
        this.discountPolicy = discountPolicy;
        // **写して持つ。** 呼び出し元が渡したあとの書き換えでこちらの中身が変わらないように
        this.lineItems = List.copyOf(lineItems);
        this.cancellationFee = cancellationFee;
        this.taxRate = taxRate;
        this.paymentStatus = paymentStatus;
        this.issuedAt = issuedAt;
    }

    /**
     * 精算書を発行する（US21-4・US21-5）。
     *
     * <p><strong>発行の時点では未入金である</strong>（決定 3）。入金の確認は US23。
     */
    public static Invoice issue(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, TransportCharge charge, DiscountPolicy discountPolicy,
            List<InvoiceLineItem> lineItems, CancellationFee cancellationFee, TaxRate taxRate,
            Instant issuedAt) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("請求番号を指定してください");
        }
        if (cargoBookingId == null) {
            throw new IllegalArgumentException("予約を指定してください");
        }
        if (shipperId == null) {
            throw new IllegalArgumentException("荷主を指定してください");
        }
        if (charge == null) {
            throw new IllegalArgumentException("基本料金の根拠を指定してください");
        }
        if (discountPolicy == null) {
            throw new IllegalArgumentException("割引方針を指定してください");
        }
        if (taxRate == null) {
            throw new IllegalArgumentException("税率を指定してください");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("発行日時を指定してください");
        }
        return new Invoice(invoiceId, cargoBookingId, shipperId, charge, discountPolicy,
                lineItems == null ? List.of() : lineItems, cancellationFee, taxRate,
                PaymentStatus.PENDING, issuedAt);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない</strong>（新しい不変条件は既存行を壊す）。
     * 検査するのは新規に受け入れるとき（{@link #issue}）である。
     */
    public static Invoice restore(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, TransportCharge charge, DiscountPolicy discountPolicy,
            List<InvoiceLineItem> lineItems, CancellationFee cancellationFee, TaxRate taxRate,
            PaymentStatus paymentStatus, Instant issuedAt) {
        return new Invoice(invoiceId, cargoBookingId, shipperId, charge, discountPolicy,
                lineItems == null ? List.of() : lineItems, cancellationFee, taxRate,
                paymentStatus, issuedAt);
    }

    public InvoiceId invoiceId() {
        return invoiceId;
    }

    public BillingBookingId cargoBookingId() {
        return cargoBookingId;
    }

    public BillingShipperId shipperId() {
        return shipperId;
    }

    /** 基本料金の根拠（決定 1）。 */
    public TransportCharge charge() {
        return charge;
    }

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

    /** キャンセル料。キャンセルされていなければ {@code null}。 */
    public CancellationFee cancellationFee() {
        return cancellationFee;
    }

    /** 調整の明細。**発行後は足せない**（決定 4）。 */
    public List<InvoiceLineItem> lineItems() {
        return lineItems;
    }

    public TaxRate taxRate() {
        return taxRate;
    }

    /** 税を含まない小計。 */
    public Money subtotal() {
        Money amount = baseAmount().subtract(discountAmount());
        if (cancellationFee != null) {
            amount = amount.add(cancellationFee.amount());
        }
        for (InvoiceLineItem item : lineItems) {
            amount = amount.add(item.amount());
        }
        return amount;
    }

    public Money taxAmount() {
        return taxRate.taxOf(subtotal());
    }

    public Money totalAmount() {
        return subtotal().add(taxAmount());
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    public Instant issuedAt() {
        return issuedAt;
    }
}
