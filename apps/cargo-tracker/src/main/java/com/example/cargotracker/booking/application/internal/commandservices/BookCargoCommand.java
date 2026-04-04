package com.example.cargotracker.booking.application.internal.commandservices;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookCargoCommand(
        String shipperId,
        String cargoType,
        BigDecimal weight,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {
}
