package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class ShipperId {

    private final UUID value;

    public ShipperId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("shipperId must not be null");
        }
        this.value = value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShipperId shipperId)) {
            return false;
        }
        return Objects.equals(value, shipperId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
