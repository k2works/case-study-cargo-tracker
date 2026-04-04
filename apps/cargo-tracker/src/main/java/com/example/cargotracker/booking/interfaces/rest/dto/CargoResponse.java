package com.example.cargotracker.booking.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CargoResponse(
        String bookingId,
        String shipperId,
        String cargoType,
        BigDecimal weight,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String status
) {}
