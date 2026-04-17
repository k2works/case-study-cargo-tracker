package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;

import java.time.LocalDateTime;

public record RecordHandlingEventCommand(
        String trackingNumber,
        TrackingEventType eventType,
        String locationUnlocode,
        LocalDateTime completionTime,
        String voyageNumber
) {}
