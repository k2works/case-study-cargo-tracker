package com.example.cargotracker.booking.domain.model.aggregates;

public enum BookingStatus {
    PRELIMINARY,
    ROUTE_PROPOSED,
    CONFIRMED,
    TRACKING_ISSUED,
    IN_TRANSIT,
    DELIVERED,
    SETTLED,
    CANCELLED
}
