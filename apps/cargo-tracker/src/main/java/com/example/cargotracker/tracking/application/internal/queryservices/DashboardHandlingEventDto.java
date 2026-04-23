package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.LocalDateTime;

public record DashboardHandlingEventDto(
        Long id,
        String trackingNumber,
        String bookingId,
        String eventType,
        String eventTypeDisplayName,
        String locationUnlocode,
        LocalDateTime completionTime
) {
    public String formattedId() {
        return String.format("HE-%04d", id);
    }
}
