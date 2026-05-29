package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.projections.TrackingEvent;

import java.time.LocalDateTime;

/**
 * 追跡イベント履歴 1 件分の REST レスポンス DTO（US17 / IT5 2.3）。
 */
public record TrackingEventResponse(
        Long eventId,
        LocalDateTime occurredAt,
        LocalDateTime recordedAt,
        String eventType,
        String transportStatus,
        String unlocode,
        String voyageNumber,
        String handlingType,
        String source,
        String description
) {
    public static TrackingEventResponse from(TrackingEvent e) {
        return new TrackingEventResponse(
                e.getEventId(),
                e.getOccurredAt(),
                e.getRecordedAt(),
                e.getEventType(),
                e.getTransportStatus(),
                e.getUnlocode(),
                e.getVoyageNumber(),
                e.getHandlingType(),
                e.getSource(),
                e.getDescription()
        );
    }
}
