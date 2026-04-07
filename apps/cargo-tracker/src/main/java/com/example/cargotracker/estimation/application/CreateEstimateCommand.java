package com.example.cargotracker.estimation.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEstimateCommand(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg
) {}
