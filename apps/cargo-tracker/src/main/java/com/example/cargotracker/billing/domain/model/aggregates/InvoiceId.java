package com.example.cargotracker.billing.domain.model.aggregates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Random;

public record InvoiceId(String value) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public InvoiceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("invoiceId must not be blank");
        }
    }

    public static InvoiceId generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        byte[] randomBytes = new byte[4];
        new Random().nextBytes(randomBytes);
        String randomPart = HexFormat.of().withUpperCase().formatHex(randomBytes);
        return new InvoiceId("INV-" + datePart + "-" + randomPart);
    }

    public static InvoiceId of(String value) {
        return new InvoiceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
