package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.LocalDateTime;
import java.util.UUID;

public record HandlingEventRecord(
        UUID id,
        UUID bookingId,
        String eventType,
        String locationCode,
        LocalDateTime completionTime,
        String memo,
        LocalDateTime registeredAt
) {
}
