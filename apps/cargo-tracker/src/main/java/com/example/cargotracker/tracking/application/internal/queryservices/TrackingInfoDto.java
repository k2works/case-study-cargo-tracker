package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 追跡情報 DTO — 追跡番号・予約 ID・荷役履歴を含む。
 */
public record TrackingInfoDto(
        String trackingNumber,
        UUID bookingId,
        List<HandlingEventSummary> handlingHistory
) {

    /**
     * 荷役履歴の 1 件分サマリー。
     */
    public record HandlingEventSummary(
            LocalDateTime completionTime,
            String locationCode,
            String eventType,
            String eventTypeDisplayName,
            String memo
    ) {}
}
