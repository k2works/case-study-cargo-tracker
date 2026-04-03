package com.example.cargotracker.exception.infrastructure.adapters;

import com.example.cargotracker.exception.application.internal.commandservices.TrackingNotFoundException;
import com.example.cargotracker.exception.application.internal.outboundservices.TrackingExistencePort;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Component;

/**
 * exception コンテキストの {@link TrackingExistencePort} を tracking コンテキストの
 * {@link TrackingRepository} に橋渡しするアダプター（アンチコラプションレイヤー）。
 */
@Component
public class TrackingExistencePortAdapter implements TrackingExistencePort {

    private final TrackingRepository trackingRepository;

    public TrackingExistencePortAdapter(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Override
    public void verifyExists(String trackingNumber) {
        trackingRepository.findByTrackingNumber(new TrackingNumber(trackingNumber))
                .orElseThrow(() -> new TrackingNotFoundException(trackingNumber));
    }
}
