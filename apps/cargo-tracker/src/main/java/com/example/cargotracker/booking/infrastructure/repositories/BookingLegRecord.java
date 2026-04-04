package com.example.cargotracker.booking.infrastructure.repositories;

import java.time.LocalDate;
import java.util.UUID;

public record BookingLegRecord(
        Long id,
        UUID bookingId,
        String voyageNumber,
        String originLocode,
        String destinationLocode,
        LocalDate departureDate,
        LocalDate arrivalDate,
        Integer legOrder
) {}
