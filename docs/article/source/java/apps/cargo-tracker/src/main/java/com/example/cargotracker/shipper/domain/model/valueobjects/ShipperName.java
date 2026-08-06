package com.example.cargotracker.shipper.domain.model.valueobjects;

public record ShipperName(String value) {

    private static final int MAX_LENGTH = 200;

    public ShipperName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shipperName must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("shipperName must be 200 characters or less");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
