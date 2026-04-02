package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

import java.util.UUID;

public class TrackingEntry {
    private final TrackingNumber trackingNumber;
    private final UUID bookingId;

    public TrackingEntry(TrackingNumber trackingNumber, UUID bookingId) {
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
    }

    public TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    public UUID getBookingId() {
        return bookingId;
    }
}
