package com.example.cargotracker.handling.domain.model.events;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;

import java.util.UUID;

/**
 * 荷役イベントが記録されたときに発行されるドメインイベント。
 */
public record HandlingEventRecordedEvent(
        HandlingEventId handlingEventId,
        UUID bookingId,
        HandlingEventType eventType
) implements DomainEvent {
}
