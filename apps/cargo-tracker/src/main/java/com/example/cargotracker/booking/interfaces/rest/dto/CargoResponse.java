package com.example.cargotracker.booking.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CargoResponse(
        String bookingId,
        String shipperId,
        String shipperName,
        String cargoType,
        String cargoTypeDisplayName,
        BigDecimal weight,
        BigDecimal dimensionLength,
        BigDecimal dimensionWidth,
        BigDecimal dimensionHeight,
        Integer quantity,
        String description,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String status,
        String statusDisplayName,
        String statusBadgeColor,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minTemperature,
        BigDecimal maxTemperature,
        String temperatureUnit,
        String trackingNumber
) {}
