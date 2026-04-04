package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.Objects;

public final class ShipperName {

    private static final int MAX_LENGTH = 200;

    private final String value;

    public ShipperName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shipperName must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("shipperName must be 200 characters or less");
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
        if (!(other instanceof ShipperName shipperName)) {
            return false;
        }
        return Objects.equals(value, shipperName.value);
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
