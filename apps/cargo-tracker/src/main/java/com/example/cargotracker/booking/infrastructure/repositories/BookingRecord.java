package com.example.cargotracker.booking.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingRecord(
        UUID id,
        UUID shipperId,
        String cargoType,
        BigDecimal cargoWeightKg,
        BigDecimal cargoLengthCm,
        BigDecimal cargoWidthCm,
        BigDecimal cargoHeightCm,
        Integer cargoQuantity,
        String cargoDescription,
        String cargoUnNumber,
        String cargoHazardClass,
        BigDecimal cargoMinTempCelsius,
        BigDecimal cargoMaxTempCelsius,
        String originLocation,
        String destinationLocation,
        LocalDate requestedPickupDate,
        LocalDate requestedDeliveryDate,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
