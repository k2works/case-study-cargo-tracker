package com.example.cargotracker.booking.domain.model.valueobjects;

import java.time.LocalDate;

public record AssignedRoute(
        String voyageNumber,
        String routePath,
        LocalDate estimatedArrival
) {
    public AssignedRoute {
        if (voyageNumber == null || voyageNumber.isBlank())
            throw new IllegalArgumentException("航海番号は必須です");
        if (routePath == null || routePath.isBlank())
            throw new IllegalArgumentException("ルートパスは必須です");
        if (estimatedArrival == null)
            throw new IllegalArgumentException("推定着日は必須です");
    }
}
