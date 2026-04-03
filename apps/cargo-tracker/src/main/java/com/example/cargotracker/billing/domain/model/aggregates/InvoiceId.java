package com.example.cargotracker.billing.domain.model.aggregates;

import java.util.UUID;

public record InvoiceId(UUID value) {

    public InvoiceId {
        if (value == null) throw new IllegalArgumentException("InvoiceId の値は null にできません");
    }

    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID());
    }

    public static InvoiceId of(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("InvoiceId の文字列値は null または空にできません");
        try {
            return new InvoiceId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("InvoiceId の形式が不正です: " + value, e);
        }
    }
}
