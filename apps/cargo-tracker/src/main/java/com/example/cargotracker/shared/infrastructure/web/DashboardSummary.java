package com.example.cargotracker.shared.infrastructure.web;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardSummary(
        long totalBookings,
        long confirmedBookings,
        long provisionalBookings,
        long pendingInvoices,
        List<HandlingEventRow> recentHandlingEvents
) {
    public record HandlingEventRow(
            String eventId,
            String bookingId,
            String eventType,
            String eventTypeLabel,
            String locationCode,
            LocalDateTime completionTime
    ) {}
}
