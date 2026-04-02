package com.example.cargotracker.tracking.interfaces.rest.dto;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;

import java.util.UUID;

public record TrackingResponse(String trackingNumber, UUID bookingId) {
    public static TrackingResponse from(TrackingEntry entry) {
        return new TrackingResponse(entry.getTrackingNumber().value(), entry.getBookingId());
    }
}
