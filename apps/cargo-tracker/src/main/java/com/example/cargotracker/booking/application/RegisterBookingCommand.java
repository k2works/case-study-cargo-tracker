package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.domain.CargoType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterBookingCommand(
        UUID shipperId,
        CargoType cargoType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        int quantity,
        String description,
        String originLocation,
        String destinationLocation,
        LocalDate requestedPickupDate,
        LocalDate requestedDeliveryDate
) {
}
