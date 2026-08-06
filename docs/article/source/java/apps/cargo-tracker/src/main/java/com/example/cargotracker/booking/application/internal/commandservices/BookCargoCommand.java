package com.example.cargotracker.booking.application.internal.commandservices;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookCargoCommand(
        String shipperId,
        String cargoType,
        BigDecimal weight,
        BigDecimal dimensionLength,
        BigDecimal dimensionWidth,
        BigDecimal dimensionHeight,
        Integer quantity,
        String description,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minTemperature,
        BigDecimal maxTemperature,
        String temperatureUnit
) {
}
