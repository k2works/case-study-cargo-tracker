package com.example.cargotracker.booking.interfaces.rest.dto;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingResponse(
        String id,
        String shipperId,
        String cargoType,
        String cargoTypeDisplayName,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        int quantity,
        String description,
        String originLocation,
        String destinationLocation,
        LocalDate requestedPickupDate,
        LocalDate requestedDeliveryDate,
        String status
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId().toString(),
                booking.getShipperId().toString(),
                booking.getCargoSpecification().cargoType().name(),
                booking.getCargoSpecification().cargoType().getDisplayName(),
                booking.getCargoSpecification().weightKg(),
                booking.getCargoSpecification().lengthCm(),
                booking.getCargoSpecification().widthCm(),
                booking.getCargoSpecification().heightCm(),
                booking.getCargoSpecification().quantity(),
                booking.getCargoSpecification().description(),
                booking.getTransportCondition().originLocation(),
                booking.getTransportCondition().destinationLocation(),
                booking.getTransportCondition().requestedPickupDate(),
                booking.getTransportCondition().requestedDeliveryDate(),
                booking.getStatus().name()
        );
    }
}
