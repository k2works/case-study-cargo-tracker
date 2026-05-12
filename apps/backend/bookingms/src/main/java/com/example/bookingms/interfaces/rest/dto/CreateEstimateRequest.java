package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEstimateRequest(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg
) {}
