package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.regex.Pattern;

public record HazardousDeclaration(String hazardousClass, String unNumber, String properShippingName) {
    private static final Pattern UN_NUMBER_PATTERN = Pattern.compile("^UN\\d{4}$");

    public HazardousDeclaration {
        if (hazardousClass == null || hazardousClass.isBlank()) {
            throw new IllegalArgumentException("hazardousClass must not be null or blank");
        }
        if (unNumber == null || unNumber.isBlank()) {
            throw new IllegalArgumentException("unNumber must not be null or blank");
        }
        if (!UN_NUMBER_PATTERN.matcher(unNumber).matches()) {
            throw new IllegalArgumentException("unNumber must match format UN followed by 4 digits (e.g. UN1203)");
        }
        if (properShippingName == null || properShippingName.isBlank()) {
            throw new IllegalArgumentException("properShippingName must not be null or blank");
        }
    }
}
