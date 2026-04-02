package com.example.cargotracker.tracking.domain.repository;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

import java.util.Optional;
import java.util.UUID;

public interface TrackingRepository {
    void save(TrackingEntry entry);
    Optional<TrackingEntry> findByTrackingNumber(TrackingNumber trackingNumber);
    Optional<TrackingEntry> findByBookingId(UUID bookingId);
}
