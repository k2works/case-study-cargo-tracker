package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.util.UUID;

public record TrackingBookingId(UUID value) {

    public TrackingBookingId {
        if (value == null) {
            throw new IllegalArgumentException("trackingBookingId must not be null");
        }
    }

    public static TrackingBookingId of(String value) {
        return new TrackingBookingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
