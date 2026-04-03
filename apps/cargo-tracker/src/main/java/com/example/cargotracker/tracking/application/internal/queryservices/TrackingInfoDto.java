package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 追跡情報 DTO — 追跡番号・予約 ID・出発地・目的地・推定到着日・現在状態・現在位置・荷役履歴を含む。
 */
public record TrackingInfoDto(
        String trackingNumber,
        UUID bookingId,
        String originLocation,
        String destinationLocation,
        LocalDate estimatedArrival,
        String currentState,
        String currentLocation,
        List<HandlingEventSummary> handlingHistory,
        List<ExceptionEventSummary> exceptionHistory
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

    /**
     * 例外履歴の 1 件分サマリー。
     */
    public record ExceptionEventSummary(
            LocalDateTime occurredAt,
            String locationCode,
            String exceptionType,
            String exceptionTypeDisplayName,
            String exceptionTypeBadgeClass,
            String reason,
            String resolution,
            String shipperNotificationStatus
    ) {}
}
