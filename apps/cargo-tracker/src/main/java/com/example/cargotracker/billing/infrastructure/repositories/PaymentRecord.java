package com.example.cargotracker.billing.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 入金の 1 行（US23）。
 *
 * <p><strong>請求書とは別のテーブルである。</strong> 入金は「いつ・いくら・どの方法で」
 * を記録する事実であり、請求書の列にすると、後から入金の履歴を持たせられなくなる。
 */
public class PaymentRecord {

    private long invoiceId;
    private BigDecimal paidAmountValue;
    private String paidAmountCurrency;
    private Instant paidAt;
    private String paymentMethod;
    private String transactionReference;

    public long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getPaidAmountValue() {
        return paidAmountValue;
    }

    public void setPaidAmountValue(BigDecimal paidAmountValue) {
        this.paidAmountValue = paidAmountValue;
    }

    public String getPaidAmountCurrency() {
        return paidAmountCurrency;
    }

    public void setPaidAmountCurrency(String paidAmountCurrency) {
        this.paidAmountCurrency = paidAmountCurrency;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    /** 取引の参照番号。<strong>無い入金もある</strong>（窓口振込など）。 */
    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }
}
