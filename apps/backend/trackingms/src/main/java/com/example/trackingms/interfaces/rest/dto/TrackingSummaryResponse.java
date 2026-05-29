package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.projections.TrackingSummary;

import java.time.LocalDateTime;

/**
 * 追跡サマリ REST レスポンス DTO（US17 / IT5 2.3）。
 */
public record TrackingSummaryResponse(
        String trackingNumber,
        String bookingId,
        String currentStatus,
        String currentUnlocode,
        String currentVoyageNumber,
        LocalDateTime estimatedArrival,
        boolean misrouted,
        LocalDateTime lastEventAt,
        LocalDateTime deliveredAt
) {
    public static TrackingSummaryResponse from(TrackingSummary p) {
        return new TrackingSummaryResponse(
                p.getTrackingNumber(),
                p.getBookingId(),
                p.getCurrentStatus(),
                p.getCurrentUnlocode(),
                p.getCurrentVoyageNumber(),
                p.getEstimatedArrival(),
                p.isMisrouted(),
                p.getLastEventAt(),
                p.getDeliveredAt()
        );
    }
}
