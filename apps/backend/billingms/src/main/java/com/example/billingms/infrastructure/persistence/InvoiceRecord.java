package com.example.billingms.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 精算書の 1 行。
 *
 * <p>MyBatis が値を差し込むため可変にしている。<strong>組み立て終えたら不変の
 * {@code Invoice} に移す</strong>。
 */
public class InvoiceRecord {

    private Long id;
    private String invoiceNumber;
    private String bookingId;
    private String shipperId;
    private String shipperName;
    private boolean shipperCorporate;
    private int legCount;
    private BigDecimal weightKg;
    private String cargoType;
    private BigDecimal baseAmountValue;
    private String baseAmountCurrency;
    private BigDecimal discountRate;
    private BigDecimal discountAmountValue;
    private String discountAmountCurrency;
    private BigDecimal cancellationFeeValue;
    private String cancellationFeeCurrency;
    private BigDecimal cancellationFeeRate;
    private String bookingStatusAtCancel;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmountValue;
    private String totalAmountCurrency;
    private String paymentStatus;
    private Instant issuedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
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

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    public boolean isShipperCorporate() {
        return shipperCorporate;
    }

    public void setShipperCorporate(boolean shipperCorporate) {
        this.shipperCorporate = shipperCorporate;
    }

    public int getLegCount() {
        return legCount;
    }

    public void setLegCount(int legCount) {
        this.legCount = legCount;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getBaseAmountValue() {
        return baseAmountValue;
    }

    public void setBaseAmountValue(BigDecimal baseAmountValue) {
        this.baseAmountValue = baseAmountValue;
    }

    public String getBaseAmountCurrency() {
        return baseAmountCurrency;
    }

    public void setBaseAmountCurrency(String baseAmountCurrency) {
        this.baseAmountCurrency = baseAmountCurrency;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    public BigDecimal getDiscountAmountValue() {
        return discountAmountValue;
    }

    public void setDiscountAmountValue(BigDecimal discountAmountValue) {
        this.discountAmountValue = discountAmountValue;
    }

    public String getDiscountAmountCurrency() {
        return discountAmountCurrency;
    }

    public void setDiscountAmountCurrency(String discountAmountCurrency) {
        this.discountAmountCurrency = discountAmountCurrency;
    }

    public BigDecimal getCancellationFeeValue() {
        return cancellationFeeValue;
    }

    public void setCancellationFeeValue(BigDecimal cancellationFeeValue) {
        this.cancellationFeeValue = cancellationFeeValue;
    }

    public String getCancellationFeeCurrency() {
        return cancellationFeeCurrency;
    }

    public void setCancellationFeeCurrency(String cancellationFeeCurrency) {
        this.cancellationFeeCurrency = cancellationFeeCurrency;
    }

    public BigDecimal getCancellationFeeRate() {
        return cancellationFeeRate;
    }

    public void setCancellationFeeRate(BigDecimal cancellationFeeRate) {
        this.cancellationFeeRate = cancellationFeeRate;
    }

    public String getBookingStatusAtCancel() {
        return bookingStatusAtCancel;
    }

    public void setBookingStatusAtCancel(String bookingStatusAtCancel) {
        this.bookingStatusAtCancel = bookingStatusAtCancel;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmountValue() {
        return totalAmountValue;
    }

    public void setTotalAmountValue(BigDecimal totalAmountValue) {
        this.totalAmountValue = totalAmountValue;
    }

    public String getTotalAmountCurrency() {
        return totalAmountCurrency;
    }

    public void setTotalAmountCurrency(String totalAmountCurrency) {
        this.totalAmountCurrency = totalAmountCurrency;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }
}
