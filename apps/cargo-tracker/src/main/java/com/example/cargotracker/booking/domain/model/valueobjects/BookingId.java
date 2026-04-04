package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.UUID;

public record BookingId(UUID value) {

    public BookingId {
        if (value == null) {
            throw new IllegalArgumentException("bookingId must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
