package com.example.cargotracker.billing.infrastructure.repositories;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code invoice} の 1 行（US21 / US22）。 */
public class InvoiceRecord {

    private long id;
    private String invoiceNumber;
    private UUID bookingId;
    private UUID shipperId;
    private int baseAmountValue;
    private String baseAmountCurrency;
    private BigDecimal discountRate;
    private Integer discountAmountValue;
    private String discountAmountCurrency;
    private BigDecimal taxRate;
    private int taxAmountValue;
    private String taxAmountCurrency;
    private int totalAmountValue;
    private String totalAmountCurrency;
    private String chargeStatus;
    private String shipperName;

    /** 法人荷主への請求か（C6）。**割引率から逆算しない。** */
    private boolean corporate;

    public boolean isCorporate() {
        return corporate;
    }

    public void setCorporate(boolean corporate) {
        this.corporate = corporate;
    }
    private String trackingNumber;
    private Integer adjustmentReductionValue;
    private Integer adjustmentCompensationValue;
    private String adjustmentCurrency;
    private String adjustmentReason;
    private long version;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getShipperId() {
        return shipperId;
    }

    public void setShipperId(UUID shipperId) {
        this.shipperId = shipperId;
    }

    public int getBaseAmountValue() {
        return baseAmountValue;
    }

    public void setBaseAmountValue(int baseAmountValue) {
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

    public Integer getDiscountAmountValue() {
        return discountAmountValue;
    }

    public void setDiscountAmountValue(Integer discountAmountValue) {
        this.discountAmountValue = discountAmountValue;
    }

    public String getDiscountAmountCurrency() {
        return discountAmountCurrency;
    }

    public void setDiscountAmountCurrency(String discountAmountCurrency) {
        this.discountAmountCurrency = discountAmountCurrency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public int getTaxAmountValue() {
        return taxAmountValue;
    }

    public void setTaxAmountValue(int taxAmountValue) {
        this.taxAmountValue = taxAmountValue;
    }

    public String getTaxAmountCurrency() {
        return taxAmountCurrency;
    }

    public void setTaxAmountCurrency(String taxAmountCurrency) {
        this.taxAmountCurrency = taxAmountCurrency;
    }

    public int getTotalAmountValue() {
        return totalAmountValue;
    }

    public void setTotalAmountValue(int totalAmountValue) {
        this.totalAmountValue = totalAmountValue;
    }

    public String getTotalAmountCurrency() {
        return totalAmountCurrency;
    }

    public void setTotalAmountCurrency(String totalAmountCurrency) {
        this.totalAmountCurrency = totalAmountCurrency;
    }

    public String getChargeStatus() {
        return chargeStatus;
    }

    public void setChargeStatus(String chargeStatus) {
        this.chargeStatus = chargeStatus;
    }

    /**
     * 宛名（凍結。IT13 レビュー C7）。
     *
     * <p><strong>荷主が改名しても、発行済みの請求書の宛名は変わらない。</strong>
     * 金額を丸め後のスナップショットで持つのと同じ理由である。
     */
    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    /** 追跡番号（凍結）。<strong>経理担当者が貨物を指す値である。</strong> */
    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Integer getAdjustmentReductionValue() {
        return adjustmentReductionValue;
    }

    public void setAdjustmentReductionValue(Integer adjustmentReductionValue) {
        this.adjustmentReductionValue = adjustmentReductionValue;
    }

    public Integer getAdjustmentCompensationValue() {
        return adjustmentCompensationValue;
    }

    public void setAdjustmentCompensationValue(Integer adjustmentCompensationValue) {
        this.adjustmentCompensationValue = adjustmentCompensationValue;
    }

    public String getAdjustmentCurrency() {
        return adjustmentCurrency;
    }

    public void setAdjustmentCurrency(String adjustmentCurrency) {
        this.adjustmentCurrency = adjustmentCurrency;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
