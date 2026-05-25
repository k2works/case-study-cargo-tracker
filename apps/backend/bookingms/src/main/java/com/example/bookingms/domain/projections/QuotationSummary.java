package com.example.bookingms.domain.projections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 見積 Read Model（US01）。quotation テーブルと従属する quotation_candidate を表す。
 */
public class QuotationSummary {

    private String quotationId;
    private String shipperId;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weightKg;
    private BigDecimal estimatedAmount;
    private String estimatedCurrency;
    private LocalDate validUntil;
    private String status;
    private List<Candidate> candidates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public QuotationSummary() { /* MyBatis result mapping */ }

    public String getQuotationId() { return quotationId; }
    public void setQuotationId(String quotationId) { this.quotationId = quotationId; }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestinationUnlocode() { return destinationUnlocode; }
    public void setDestinationUnlocode(String destinationUnlocode) { this.destinationUnlocode = destinationUnlocode; }

    public LocalDate getArrivalDeadline() { return arrivalDeadline; }
    public void setArrivalDeadline(LocalDate arrivalDeadline) { this.arrivalDeadline = arrivalDeadline; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }

    public String getEstimatedCurrency() { return estimatedCurrency; }
    public void setEstimatedCurrency(String estimatedCurrency) { this.estimatedCurrency = estimatedCurrency; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Candidate> getCandidates() { return candidates; }
    public void setCandidates(List<Candidate> candidates) { this.candidates = candidates; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public static class Candidate {
        private int candidateSeq;
        private int estimatedDays;
        private BigDecimal estimatedCost;
        private String estimatedCurrency;
        private String itinerarySummary;

        public Candidate() { /* MyBatis result mapping */ }

        public int getCandidateSeq() { return candidateSeq; }
        public void setCandidateSeq(int candidateSeq) { this.candidateSeq = candidateSeq; }

        public int getEstimatedDays() { return estimatedDays; }
        public void setEstimatedDays(int estimatedDays) { this.estimatedDays = estimatedDays; }

        public BigDecimal getEstimatedCost() { return estimatedCost; }
        public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }

        public String getEstimatedCurrency() { return estimatedCurrency; }
        public void setEstimatedCurrency(String estimatedCurrency) { this.estimatedCurrency = estimatedCurrency; }

        public String getItinerarySummary() { return itinerarySummary; }
        public void setItinerarySummary(String itinerarySummary) { this.itinerarySummary = itinerarySummary; }
    }
}
