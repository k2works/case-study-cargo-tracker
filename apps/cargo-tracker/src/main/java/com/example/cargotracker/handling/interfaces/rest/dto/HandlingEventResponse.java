package com.example.cargotracker.handling.interfaces.rest.dto;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record HandlingEventResponse(
        UUID id,
        UUID bookingId,
        HandlingEventType eventType,
        String locationCode,
        LocalDateTime completionTime,
        String memo
) {
    public static HandlingEventResponse from(HandlingEvent event) {
        return new HandlingEventResponse(
                event.getId().value(),
                event.getBookingId(),
                event.getEventType(),
                event.getLocationCode(),
                event.getCompletionTime(),
                event.getMemo()
        );
    }
}
