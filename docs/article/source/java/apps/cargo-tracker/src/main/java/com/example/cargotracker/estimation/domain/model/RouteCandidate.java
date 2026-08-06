package com.example.cargotracker.estimation.domain.model;

import java.math.BigDecimal;

public record RouteCandidate(
        String voyageNumber,
        String transitPort,
        int transitDays,
        BigDecimal estimatedCost
) {
    public RouteCandidate {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("voyageNumber must not be blank");
        }
        if (transitDays <= 0) {
            throw new IllegalArgumentException("transitDays must be positive");
        }
        if (estimatedCost == null || estimatedCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("estimatedCost must be positive");
        }
    }
}
