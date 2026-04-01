package com.example.cargotracker.shipper.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ShipperId {

    private final UUID value;

    public ShipperId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ShipperId の値は null にできません");
        }
        this.value = value;
    }

    public static ShipperId generate() {
        return new ShipperId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShipperId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
