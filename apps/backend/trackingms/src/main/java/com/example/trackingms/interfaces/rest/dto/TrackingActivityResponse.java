package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 追跡アクティビティ レスポンス DTO
 */
public record TrackingActivityResponse(
        String trackingNumber,
        String bookingId,
        String transportStatus,
        List<TrackingActivityEventResponse> events
) {
    public record TrackingActivityEventResponse(
            String eventType,
            String locationUnlocode,
            LocalDateTime eventTime,
            String voyageNumber
    ) {}

    public static TrackingActivityResponse from(TrackingActivity activity) {
        List<TrackingActivityEventResponse> eventResponses = activity.getEvents().stream()
                .map(e -> new TrackingActivityEventResponse(
                        e.getEventType().name(),
                        e.getLocationUnlocode(),
                        e.getEventTime(),
                        e.getVoyageNumber()))
                .toList();

        return new TrackingActivityResponse(
                activity.getTrackingNumber().getNumber(),
                activity.getBookingId().getBookingId(),
                activity.getTransportStatus().name(),
                eventResponses
        );
    }
}
