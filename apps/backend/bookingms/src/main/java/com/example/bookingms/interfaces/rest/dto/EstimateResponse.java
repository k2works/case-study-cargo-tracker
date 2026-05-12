package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.aggregates.Estimate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EstimateResponse(
        UUID estimateId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        String status,
        List<RouteCandidateResponse> candidates
) {
    public static EstimateResponse from(Estimate estimate) {
        return new EstimateResponse(
                estimate.getEstimateId(),
                estimate.getOriginUnlocode(),
                estimate.getDestinationUnlocode(),
                estimate.getArrivalDeadline(),
                estimate.getCargoType().name(),
                estimate.getWeightKg(),
                estimate.getStatus().name(),
                estimate.getCandidates().stream()
                        .map(RouteCandidateResponse::from)
                        .toList()
        );
    }
}
