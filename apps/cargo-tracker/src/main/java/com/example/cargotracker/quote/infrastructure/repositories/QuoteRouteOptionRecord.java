package com.example.cargotracker.quote.infrastructure.repositories;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteRouteOptionRecord(
        Long id,
        UUID quoteId,
        String voyageNumber,
        String viaLocodes,
        Integer transitDays,
        BigDecimal estimatedPrice,
        Integer sortOrder
) {
}
