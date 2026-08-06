package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public record TrackingNumber(String value) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    public TrackingNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
    }

    public static TrackingNumber generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        byte[] randomBytes = new byte[4];
        RANDOM.nextBytes(randomBytes);
        String randomPart = HexFormat.of().withUpperCase().formatHex(randomBytes);
        return new TrackingNumber("TRK-" + datePart + "-" + randomPart);
    }

    public static TrackingNumber of(String value) {
        return new TrackingNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
