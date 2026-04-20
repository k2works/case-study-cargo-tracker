package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TrackingDetailDto(
        String trackingNumber,
        String bookingId,
        CargoTrackingStatus status,
        List<TrackingEventDto> events
) {
    public record TrackingEventDto(
            String eventType,
            String eventTypeDisplayName,
            String locationUnlocode,
            LocalDateTime completionTime,
            String voyageNumber
    ) {}
}
