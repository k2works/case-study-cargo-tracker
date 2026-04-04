package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.LocalDate;

/**
 * 航海区間（voyage_legs）の DB レコード。
 */
public record VoyageLegRecord(
    Long id,
    String voyageNumber,
    String originLocode,
    String destinationLocode,
    LocalDate departureDate,
    LocalDate arrivalDate,
    Integer legOrder
) {}
