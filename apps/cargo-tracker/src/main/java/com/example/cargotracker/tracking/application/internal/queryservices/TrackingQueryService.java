package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TrackingQueryService {

    private final TrackingRepository trackingRepository;

    public TrackingQueryService(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    public Optional<TrackingEntry> findByTrackingNumber(String trackingNumberValue) {
        return trackingRepository.findByTrackingNumber(new TrackingNumber(trackingNumberValue));
    }
}
