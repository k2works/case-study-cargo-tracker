package com.example.cargotracker.booking.domain.model.commands;

import java.time.LocalDate;
import java.util.UUID;

public record AssignRouteCommand(
        UUID bookingId,
        String voyageNumber,
        String routePath,
        LocalDate estimatedArrival
) {
}
