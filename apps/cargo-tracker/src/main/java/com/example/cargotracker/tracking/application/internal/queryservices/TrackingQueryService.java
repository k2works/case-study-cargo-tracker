package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.infrastructure.repositories.HandlingEventRecord;
import com.example.cargotracker.tracking.infrastructure.repositories.TrackingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TrackingQueryService {

    private final TrackingMapper trackingMapper;

    public TrackingQueryService(TrackingMapper trackingMapper) {
        this.trackingMapper = trackingMapper;
    }

    @Transactional(readOnly = true)
    public Optional<TrackingDetailDto> findByTrackingNumber(String trackingNumber) {
        var trackingRecord = trackingMapper.findByTrackingNumber(trackingNumber);
        if (trackingRecord == null) {
            return Optional.empty();
        }
        List<HandlingEventRecord> events = trackingMapper.findHandlingEventsByTrackingNumber(trackingNumber);
        List<TrackingDetailDto.TrackingEventDto> eventDtos = events.stream()
                .map(e -> {
                    String displayName;
                    try {
                        displayName = TrackingEventType.valueOf(e.getEventType()).getDisplayName();
                    } catch (IllegalArgumentException ignored) {
                        displayName = e.getEventType();
                    }
                    return new TrackingDetailDto.TrackingEventDto(
                            e.getEventType(),
                            displayName,
                            e.getLocationUnlocode(),
                            e.getCompletionTime(),
                            e.getVoyageNumber()
                    );
                })
                .toList();
        com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus status =
                com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus.valueOf(trackingRecord.getCargoStatus());
        return Optional.of(new TrackingDetailDto(
                trackingRecord.getTrackingNumber(),
                trackingRecord.getBookingId(),
                status,
                eventDtos
        ));
    }
}
