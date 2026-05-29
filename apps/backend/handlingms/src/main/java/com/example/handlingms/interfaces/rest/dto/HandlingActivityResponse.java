package com.example.handlingms.interfaces.rest.dto;

import com.example.handlingms.domain.projections.HandlingActivitySummary;

import java.time.LocalDateTime;

/**
 * 荷役作業 REST レスポンス DTO（US15・US16 / IT5 3.x）。
 */
public record HandlingActivityResponse(
        String activityId,
        String bookingId,
        String trackingNumber,
        String originUnlocode,
        String destinationUnlocode,
        String cargoType,
        String handlingType,
        LocalDateTime occurredAt,
        LocalDateTime recordedAt,
        String unlocode,
        String voyageNumber,
        String handlerId,
        boolean unexpected
) {
    public static HandlingActivityResponse from(HandlingActivitySummary s) {
        return new HandlingActivityResponse(
                s.getActivityId(), s.getBookingId(), s.getTrackingNumber(),
                s.getOriginUnlocode(), s.getDestinationUnlocode(), s.getCargoType(),
                s.getHandlingType(), s.getOccurredAt(), s.getRecordedAt(),
                s.getUnlocode(), s.getVoyageNumber(), s.getHandlerId(),
                s.isUnexpected()
        );
    }
}
