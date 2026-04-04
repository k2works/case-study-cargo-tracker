package com.example.cargotracker.shipper.domain.model.valueobjects;

public record Address(String value) {

    private static final int MAX_LENGTH = 500;

    public Address {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("address must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
