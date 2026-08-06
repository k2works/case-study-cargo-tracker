package com.example.cargotracker.booking.domain.model.valueobjects;

public record Description(String value) {
    private static final int MAX_LENGTH = 500;

    public Description {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("description must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
