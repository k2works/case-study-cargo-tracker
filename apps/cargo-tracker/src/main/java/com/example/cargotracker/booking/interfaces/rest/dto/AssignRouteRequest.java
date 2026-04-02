package com.example.cargotracker.booking.interfaces.rest.dto;

import java.time.LocalDate;

public record AssignRouteRequest(
        String voyageNumber,
        String routePath,
        LocalDate estimatedArrival
) {
}
