package com.example.cargotracker.estimation.infrastructure.repositories;

import java.math.BigDecimal;

/** {@code route_candidate} テーブルの行。 */
public class RouteCandidateRecord {

    private long estimateId;
    private String voyageNumber;
    private String transitPort;
    private int transitDays;
    private BigDecimal estimatedCostValue;
    private String estimatedCostCurrency;
    private int priority;

    public long getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(long estimateId) {
        this.estimateId = estimateId;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getTransitPort() {
        return transitPort;
    }

    public void setTransitPort(String transitPort) {
        this.transitPort = transitPort;
    }

    public int getTransitDays() {
        return transitDays;
    }

    public void setTransitDays(int transitDays) {
        this.transitDays = transitDays;
    }

    public BigDecimal getEstimatedCostValue() {
        return estimatedCostValue;
    }

    public void setEstimatedCostValue(BigDecimal estimatedCostValue) {
        this.estimatedCostValue = estimatedCostValue;
    }

    public String getEstimatedCostCurrency() {
        return estimatedCostCurrency;
    }

    public void setEstimatedCostCurrency(String estimatedCostCurrency) {
        this.estimatedCostCurrency = estimatedCostCurrency;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
