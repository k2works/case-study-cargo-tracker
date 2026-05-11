package com.example.billingms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * invoice テーブルの永続化レコード
 */
public class InvoiceRecord {

    private Long id;
    private String invoiceNumber;
    private String bookingId;
    private String shipperId;
    private Long baseAmountValue;
    private String baseAmountCurrency;
    private Long finalAmountValue;
    private String finalAmountCurrency;
    private BigDecimal taxRate;
    private Long taxAmountValue;
    private BigDecimal discountRate;
    private Long discountAmountValue;
    private String paymentStatus;
    private LocalDate issuedAt;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public Long getBaseAmountValue() { return baseAmountValue; }
    public void setBaseAmountValue(Long baseAmountValue) { this.baseAmountValue = baseAmountValue; }

    public String getBaseAmountCurrency() { return baseAmountCurrency; }
    public void setBaseAmountCurrency(String baseAmountCurrency) { this.baseAmountCurrency = baseAmountCurrency; }

    public Long getFinalAmountValue() { return finalAmountValue; }
    public void setFinalAmountValue(Long finalAmountValue) { this.finalAmountValue = finalAmountValue; }

    public String getFinalAmountCurrency() { return finalAmountCurrency; }
    public void setFinalAmountCurrency(String finalAmountCurrency) { this.finalAmountCurrency = finalAmountCurrency; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public Long getTaxAmountValue() { return taxAmountValue; }
    public void setTaxAmountValue(Long taxAmountValue) { this.taxAmountValue = taxAmountValue; }

    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }

    public Long getDiscountAmountValue() { return discountAmountValue; }
    public void setDiscountAmountValue(Long discountAmountValue) { this.discountAmountValue = discountAmountValue; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
