package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.Objects;

public final class ShipperCode {

    private final String value;

    public ShipperCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shipperCode must not be blank");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShipperCode shipperCode)) {
            return false;
        }
        return Objects.equals(value, shipperCode.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
