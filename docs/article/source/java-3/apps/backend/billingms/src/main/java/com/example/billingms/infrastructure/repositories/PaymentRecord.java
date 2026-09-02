package com.example.billingms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 入金の行（[ADR-028] 決定 2）。 */
public class PaymentRecord {

    private Long invoiceId;

    private BigDecimal paidAmountValue;

    private String paidAmountCurrency;

    private LocalDate paidAt;

    private String paymentMethod;

    private String transactionReference;

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
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

    public LocalDate getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDate paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }
}
