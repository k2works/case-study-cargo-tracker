package com.example.cargotracker.quote.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record QuoteRecord(
        UUID id,
        String quoteNumber,
        String originLocode,
        String destinationLocode,
        LocalDate requestedArrivalDate,
        String cargoType,
        BigDecimal weightKg,
        LocalDateTime createdAt
) {
}
