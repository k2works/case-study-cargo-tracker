package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.projections.QuotationSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 輸送見積レスポンス（US01）。
 */
public record QuotationResponse(
        String quotationId,
        String shipperId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal estimatedAmount,
        String estimatedCurrency,
        LocalDate validUntil,
        String status,
        List<CandidateResponse> candidates
) {
    public static QuotationResponse from(QuotationSummary summary) {
        List<CandidateResponse> candidates = summary.getCandidates() == null ? List.of() :
                summary.getCandidates().stream()
                        .map(c -> new CandidateResponse(
                                c.getCandidateSeq(),
                                c.getEstimatedDays(),
                                c.getEstimatedCost(),
                                c.getEstimatedCurrency(),
                                c.getItinerarySummary()))
                        .toList();
        return new QuotationResponse(
                summary.getQuotationId(),
                summary.getShipperId(),
                summary.getOriginUnlocode(),
                summary.getDestinationUnlocode(),
                summary.getArrivalDeadline(),
                summary.getCargoType(),
                summary.getWeightKg(),
                summary.getEstimatedAmount(),
                summary.getEstimatedCurrency(),
                summary.getValidUntil(),
                summary.getStatus(),
                candidates);
    }

    public record CandidateResponse(
            int candidateSeq,
            int estimatedDays,
            BigDecimal estimatedCost,
            String estimatedCurrency,
            String itinerarySummary
    ) {
    }
}
