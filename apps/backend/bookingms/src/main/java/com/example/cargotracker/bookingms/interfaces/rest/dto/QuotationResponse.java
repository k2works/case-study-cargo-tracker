package com.example.cargotracker.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積詳細レスポンス DTO（US01）。
 *
 * <p>{@code candidates} は受入条件 5 で「期限内ルートなし」を表す場合は空配列を返す。</p>
 */
public record QuotationResponse(
        String quotationId,
        Long shipperId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal estimatedAmount,
        String estimatedCurrency,
        LocalDate validUntil,
        String status,
        String hazardImoClass,
        String hazardUnNumber,
        String hazardDeclaration,
        List<RouteCandidateDto> candidates) {

    public record RouteCandidateDto(
            int candidateSeq,
            int estimatedDays,
            BigDecimal estimatedCost,
            String estimatedCurrency,
            String itinerarySummary,
            String voyageNumbers) {
    }
}
