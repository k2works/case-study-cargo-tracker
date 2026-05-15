package com.example.cargotracker.bookingms.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * quotation テーブルの 1 行を表す POJO（US01 Read Model）。
 *
 * <p>data-model.md L350 の {@code quotation} スキーマに対応。
 * MyBatis Mapper の入出力に使用する。</p>
 */
public class QuotationRecord {

    private String quotationId;
    private Long shipperId;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weightKg;
    private BigDecimal estimatedAmount;
    private String estimatedCurrency;
    private LocalDate validUntil;
    private String status;
    private String hazardImoClass;
    private String hazardUnNumber;
    private String hazardDeclaration;

    public String getQuotationId() {
        return quotationId;
    }

    public void setQuotationId(String quotationId) {
        this.quotationId = quotationId;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public void setOriginUnlocode(String originUnlocode) {
        this.originUnlocode = originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public void setDestinationUnlocode(String destinationUnlocode) {
        this.destinationUnlocode = destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public void setEstimatedAmount(BigDecimal estimatedAmount) {
        this.estimatedAmount = estimatedAmount;
    }

    public String getEstimatedCurrency() {
        return estimatedCurrency;
    }

    public void setEstimatedCurrency(String estimatedCurrency) {
        this.estimatedCurrency = estimatedCurrency;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHazardImoClass() {
        return hazardImoClass;
    }

    public void setHazardImoClass(String hazardImoClass) {
        this.hazardImoClass = hazardImoClass;
    }

    public String getHazardUnNumber() {
        return hazardUnNumber;
    }

    public void setHazardUnNumber(String hazardUnNumber) {
        this.hazardUnNumber = hazardUnNumber;
    }

    public String getHazardDeclaration() {
        return hazardDeclaration;
    }

    public void setHazardDeclaration(String hazardDeclaration) {
        this.hazardDeclaration = hazardDeclaration;
    }
}
