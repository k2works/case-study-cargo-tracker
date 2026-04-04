package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.UUID;

public record ShipperId(UUID value) {

    public ShipperId {
        if (value == null) {
            throw new IllegalArgumentException("shipperId must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
