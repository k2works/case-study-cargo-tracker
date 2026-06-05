package com.example.billingms.domain.projections;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金履歴 Read Model（{@code payment} テーブル、IT7 US23）。
 *
 * <p>data-model.md L724-733 の payment テーブル定義に対応。
 * IT7 は完全一致のみ受理（1 invoice 1 payment）、IT8 で部分入金対応予定。</p>
 */
public class Payment {

    private String paymentId;
    private String invoiceId;
    private BigDecimal paidAmount;
    private String currency;
    private LocalDateTime paidAt;
    private String paymentMethod;
    private String externalReference;

    public Payment() { /* MyBatis result mapping */ }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
}
