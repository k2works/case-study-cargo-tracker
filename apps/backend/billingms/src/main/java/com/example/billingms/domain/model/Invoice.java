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
    private final InvoiceCharges charges;
    private final List<InvoiceLineItem> lineItems;
    private final PaymentStatus paymentStatus;
    private final Instant issuedAt;

    private Invoice(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, InvoiceCharges charges,
            List<InvoiceLineItem> lineItems, PaymentStatus paymentStatus, Instant issuedAt) {
        this.invoiceId = invoiceId;
        this.cargoBookingId = cargoBookingId;
        this.shipperId = shipperId;
        this.charges = charges;
        // **写して持つ。** 呼び出し元が渡したあとの書き換えでこちらの中身が変わらないように
        this.lineItems = List.copyOf(lineItems);
        this.paymentStatus = paymentStatus;
        this.issuedAt = issuedAt;
    }

    /**
     * 精算書を発行する（US21-4・US21-5）。
     *
     * <p><strong>発行の時点では未入金である</strong>（決定 3）。入金の確認は US23。
     */
    public static Invoice issue(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, InvoiceCharges charges,
            List<InvoiceLineItem> lineItems, Instant issuedAt) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("請求番号を指定してください");
        }
        if (cargoBookingId == null) {
            throw new IllegalArgumentException("予約を指定してください");
        }
        if (shipperId == null) {
            throw new IllegalArgumentException("荷主を指定してください");
        }
        if (charges == null) {
            throw new IllegalArgumentException("金額の材料を指定してください");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("発行日時を指定してください");
        }
        return new Invoice(invoiceId, cargoBookingId, shipperId, charges,
                lineItems == null ? List.of() : lineItems, PaymentStatus.PENDING, issuedAt);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない</strong>（新しい不変条件は既存行を壊す）。
     * 検査するのは新規に受け入れるとき（{@link #issue}）である。
     */
    public static Invoice restore(InvoiceId invoiceId, BillingBookingId cargoBookingId,
            BillingShipperId shipperId, InvoiceCharges charges,
            List<InvoiceLineItem> lineItems, PaymentStatus paymentStatus, Instant issuedAt) {
        return new Invoice(invoiceId, cargoBookingId, shipperId, charges,
                lineItems == null ? List.of() : lineItems, paymentStatus, issuedAt);
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

    /**
     * 発行した時点の荷主の社名。
     *
     * <p><strong>荷主 ID から毎回引き直さない。</strong>社名を変えた途端に発行済みの
     * 請求書の宛名まで変わるのは、出した書面が後から書き換わるのと同じである
     * （決定 4 が禁じていること）。
     */
    public String shipperName() {
        return shipperId.name();
    }

    /** 金額の材料（決定 1・決定 6・決定 8）。 */
    public InvoiceCharges charges() {
        return charges;
    }

    /** 基本料金の根拠（決定 1）。 */
    public TransportCharge charge() {
        return charges.charge();
    }

    public Money baseAmount() {
        return charges.baseAmount();
    }

    /** 割引率。**割引が無ければ {@code null}**——0% と契約なしを区別する（[ADR-012]）。 */
    public DiscountRate discountRate() {
        return charges.discountRate();
    }

    public Money discountAmount() {
        return charges.discountAmount();
    }

    /** キャンセル料。キャンセルされていなければ {@code null}。 */
    public CancellationFee cancellationFee() {
        return charges.cancellationFee();
    }

    /** 調整の明細。**発行後は足せない**（決定 4）。 */
    public List<InvoiceLineItem> lineItems() {
        return lineItems;
    }

    public TaxRate taxRate() {
        return charges.taxRate();
    }

    /** 税を含まない小計。 */
    public Money subtotal() {
        Money amount = charges.subtotalBeforeAdjustments();
        for (InvoiceLineItem item : lineItems) {
            amount = amount.add(item.amount());
        }
        return amount;
    }

    public Money taxAmount() {
        return charges.taxRate().taxOf(subtotal());
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
