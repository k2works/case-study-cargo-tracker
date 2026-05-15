package com.example.cargotracker.bookingms.infrastructure.persistence;

import java.math.BigDecimal;

/**
 * quotation_candidate テーブルの 1 行を表す POJO（US01 Read Model）。
 *
 * <p>data-model.md L368 の {@code quotation_candidate} スキーマに対応。</p>
 */
public class QuotationCandidateRecord {

    private String quotationId;
    private Integer candidateSeq;
    private Integer estimatedDays;
    private BigDecimal estimatedCost;
    private String estimatedCurrency;
    private String itinerarySummary;
    private String voyageNumbers;

    public String getQuotationId() {
        return quotationId;
    }

    public void setQuotationId(String quotationId) {
        this.quotationId = quotationId;
    }

    public Integer getCandidateSeq() {
        return candidateSeq;
    }

    public void setCandidateSeq(Integer candidateSeq) {
        this.candidateSeq = candidateSeq;
    }

    public Integer getEstimatedDays() {
        return estimatedDays;
    }

    public void setEstimatedDays(Integer estimatedDays) {
        this.estimatedDays = estimatedDays;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public String getEstimatedCurrency() {
        return estimatedCurrency;
    }

    public void setEstimatedCurrency(String estimatedCurrency) {
        this.estimatedCurrency = estimatedCurrency;
    }

    public String getItinerarySummary() {
        return itinerarySummary;
    }

    public void setItinerarySummary(String itinerarySummary) {
        this.itinerarySummary = itinerarySummary;
    }

    public String getVoyageNumbers() {
        return voyageNumbers;
    }

    public void setVoyageNumbers(String voyageNumbers) {
        this.voyageNumbers = voyageNumbers;
    }
}
