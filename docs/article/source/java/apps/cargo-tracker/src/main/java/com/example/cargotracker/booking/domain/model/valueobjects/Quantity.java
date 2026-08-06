package com.example.cargotracker.booking.domain.model.valueobjects;

public record Quantity(int value) {
    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }
}
