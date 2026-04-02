package com.example.cargotracker.tracking.infrastructure.repositories;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TrackingRepositoryImpl implements TrackingRepository {

    private final TrackingMapper trackingMapper;

    public TrackingRepositoryImpl(TrackingMapper trackingMapper) {
        this.trackingMapper = trackingMapper;
    }

    @Override
    public void save(TrackingEntry entry) {
        trackingMapper.insert(
                entry.getTrackingNumber().value(),
                entry.getBookingId().toString()
        );
    }

    @Override
    public Optional<TrackingEntry> findByTrackingNumber(TrackingNumber trackingNumber) {
        return trackingMapper.findByTrackingNumber(trackingNumber.value())
                .map(r -> new TrackingEntry(
                        new TrackingNumber(r.trackingNumber()),
                        UUID.fromString(r.bookingId())
                ));
    }

    @Override
    public Optional<TrackingEntry> findByBookingId(UUID bookingId) {
        return trackingMapper.findByBookingId(bookingId.toString())
                .map(r -> new TrackingEntry(
                        new TrackingNumber(r.trackingNumber()),
                        UUID.fromString(r.bookingId())
                ));
    }
}
