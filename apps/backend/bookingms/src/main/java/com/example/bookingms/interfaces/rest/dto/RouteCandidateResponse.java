package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.entities.RouteCandidate;

import java.math.BigDecimal;

public record RouteCandidateResponse(
        String voyageNumber,
        String transitPort,
        int transitDays,
        BigDecimal estimatedCost,
        int rank
) {
    public static RouteCandidateResponse from(RouteCandidate candidate) {
        return new RouteCandidateResponse(
                candidate.getVoyageNumber(),
                candidate.getTransitPort(),
                candidate.getTransitDays(),
                candidate.getEstimatedCost(),
                candidate.getRank()
        );
    }
}
