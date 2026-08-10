package com.example.cargotracker.billing.infrastructure.repositories;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code invoice} の 1 行（US21 / US22）。 */
public class InvoiceRecord {

    private long id;
    private String invoiceNumber;
    private UUID bookingId;
    private UUID shipperId;
    private boolean corporate;
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

    /**
     * 法人荷主か。
     *
     * <p><strong>invoice には持たない。</strong> 荷主種別は Shipper の持ち物であり、
     * 請求書に写すと契約が変わったときに 2 か所が食い違う。
     * <strong>復元では割引率から判断する</strong>（率が付いているのは法人だけである）。
     */
    public boolean isCorporate() {
        return corporate;
    }

    public void setCorporate(boolean corporate) {
        this.corporate = corporate;
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
