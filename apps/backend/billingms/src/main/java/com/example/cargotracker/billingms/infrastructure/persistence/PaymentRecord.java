package com.example.cargotracker.billingms.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment テーブルの 1 行を表す POJO（US23）。
 */
public class PaymentRecord {

    private String paymentId;
    private String invoiceId;
    private BigDecimal paidAmount;
    private String currency;
    private LocalDateTime paidAt;
    private String paymentMethod;
    private String externalReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public PaymentRecord() {
    }

    public PaymentRecord(
            String paymentId,
            String invoiceId,
            BigDecimal paidAmount,
            String currency,
            LocalDateTime paidAt,
            String paymentMethod,
            String externalReference) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.paidAmount = paidAmount;
        this.currency = currency;
        this.paidAt = paidAt;
        this.paymentMethod = paymentMethod;
        this.externalReference = externalReference;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
