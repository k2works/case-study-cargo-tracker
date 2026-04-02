package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public Optional<TrackingEntry> findByBookingId(UUID bookingId) {
        return trackingRepository.findByBookingId(bookingId);
    }

    /**
     * 追跡番号で追跡情報（荷役履歴含む）を取得する。
     */
    public Optional<TrackingInfoDto> findTrackingInfo(String trackingNumberValue) {
        TrackingNumber trackingNumber = new TrackingNumber(trackingNumberValue);
        return trackingRepository.findByTrackingNumber(trackingNumber)
                .map(entry -> {
                    List<TrackingInfoDto.HandlingEventSummary> history =
                            trackingRepository.findHandlingEventsByTrackingNumber(trackingNumber)
                                    .stream()
                                    .map(r -> new TrackingInfoDto.HandlingEventSummary(
                                            r.completionTime(),
                                            r.locationCode(),
                                            r.eventType(),
                                            resolveDisplayName(r.eventType()),
                                            r.memo()
                                    ))
                                    .toList();
                    return new TrackingInfoDto(
                            entry.getTrackingNumber().value(),
                            entry.getBookingId(),
                            history
                    );
                });
    }

    private String resolveDisplayName(String eventType) {
        try {
            return HandlingEventType.valueOf(eventType).getDisplayName();
        } catch (IllegalArgumentException e) {
            return eventType;
        }
    }
}
