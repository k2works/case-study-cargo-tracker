package com.example.cargotracker.shipper.domain.model.valueobjects;

public record ShipperCode(String value) {

    public ShipperCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shipperCode must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
