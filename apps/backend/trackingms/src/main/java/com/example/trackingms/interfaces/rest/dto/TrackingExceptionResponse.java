package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.projections.TrackingExceptionView;

import java.time.LocalDateTime;

/**
 * 追跡例外 REST レスポンス DTO（US19 / US20）。
 */
public record TrackingExceptionResponse(
        String exceptionId,
        String trackingNumber,
        String type,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String responseStatus,
        String resolution,
        LocalDateTime resolvedAt,
        boolean escalated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TrackingExceptionResponse from(TrackingExceptionView v) {
        return new TrackingExceptionResponse(
                v.getExceptionId(),
                v.getTrackingNumber(),
                v.getExceptionType(),
                v.getOccurredAt(),
                v.getOccurredUnlocode(),
                v.getDescription(),
                v.getResponseStatus(),
                v.getResolution(),
                v.getResolvedAt(),
                v.isEscalated(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
