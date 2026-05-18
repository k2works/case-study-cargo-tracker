package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * US18 公開追跡照会レスポンス。
 */
public record TrackingInfoResponse(
        String trackingNumber,
        String currentStatus,
        Location currentLocation,
        LocalDateTime estimatedArrival,
        LocalDateTime deliveredAt,
        boolean misrouted,
        LocalDateTime validUntil,
        List<TrackingEvent> events) {

    public record Location(String unlocode, String portName) {}

    public record TrackingEvent(
            LocalDateTime occurredAt,
            String type,
            String unlocode,
            String voyageNumber,
            String transportStatus,
            String handlingType,
            String source,
            String description) {}
}
