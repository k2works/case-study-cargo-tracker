package com.example.cargotracker.tracking.interfaces.rest.dto;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingInfoDto;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TrackingResponse(
        String trackingNumber,
        UUID bookingId,
        List<HandlingEventSummaryResponse> handlingHistory
) {
    public static TrackingResponse from(TrackingEntry entry) {
        return new TrackingResponse(entry.getTrackingNumber().value(), entry.getBookingId(), List.of());
    }

    public static TrackingResponse from(TrackingInfoDto dto) {
        List<HandlingEventSummaryResponse> history = dto.handlingHistory().stream()
                .map(s -> new HandlingEventSummaryResponse(
                        s.completionTime(),
                        s.locationCode(),
                        s.eventType(),
                        s.eventTypeDisplayName(),
                        s.memo()
                ))
                .toList();
        return new TrackingResponse(dto.trackingNumber(), dto.bookingId(), history);
    }

    public record HandlingEventSummaryResponse(
            LocalDateTime completionTime,
            String locationCode,
            String eventType,
            String eventTypeDisplayName,
            String memo
    ) {}
}
