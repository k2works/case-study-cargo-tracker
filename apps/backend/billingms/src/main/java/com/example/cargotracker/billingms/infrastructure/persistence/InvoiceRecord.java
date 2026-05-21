package com.example.cargotracker.billingms.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * invoice テーブルの 1 行を表す POJO（US21）。
 *
 * <p>MyBatis のデフォルトリフレクションは record の {@code <init>} を自動解決できないため、
 * trackingms と合わせて POJO を採用する。</p>
 */
public class InvoiceRecord {

    private String invoiceId;
    private String bookingId;
    private String shipperId;
    private BigDecimal basicAmount;
    private BigDecimal discountAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal totalAmount;
    private String currency;
    private String billingStatus;
    private String invoiceNumber;
    private LocalDate paymentDue;
    private LocalDateTime paidAt;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public InvoiceRecord() {
    }

    @SuppressWarnings("java:S107") // MyBatis POJO: invoice テーブルの全カラムに対応するため必要
    public InvoiceRecord(
            String invoiceId,
            String bookingId,
            String shipperId,
            BigDecimal basicAmount,
            BigDecimal discountAmount,
            BigDecimal adjustmentAmount,
            BigDecimal totalAmount,
            String currency,
            String billingStatus,
            String invoiceNumber,
            LocalDate paymentDue,
            LocalDateTime paidAt,
            String cancellationReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {
        this.invoiceId = invoiceId;
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.basicAmount = basicAmount;
        this.discountAmount = discountAmount;
        this.adjustmentAmount = adjustmentAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.billingStatus = billingStatus;
        this.invoiceNumber = invoiceNumber;
        this.paymentDue = paymentDue;
        this.paidAt = paidAt;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getShipperId() {
        return shipperId;
    }

    public void setShipperId(String shipperId) {
        this.shipperId = shipperId;
    }

    public BigDecimal getBasicAmount() {
        return basicAmount;
    }

    public void setBasicAmount(BigDecimal basicAmount) {
        this.basicAmount = basicAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public void setAdjustmentAmount(BigDecimal adjustmentAmount) {
        this.adjustmentAmount = adjustmentAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBillingStatus() {
        return billingStatus;
    }

    public void setBillingStatus(String billingStatus) {
        this.billingStatus = billingStatus;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getPaymentDue() {
        return paymentDue;
    }

    public void setPaymentDue(LocalDate paymentDue) {
        this.paymentDue = paymentDue;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
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
