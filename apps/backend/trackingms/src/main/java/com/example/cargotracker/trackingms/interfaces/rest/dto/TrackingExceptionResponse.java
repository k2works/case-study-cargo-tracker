package com.example.cargotracker.trackingms.interfaces.rest.dto;

import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingExceptionRecord;
import java.time.LocalDateTime;

/**
 * US19/US20 追跡例外レスポンス DTO。
 *
 * <p>{@link TrackingExceptionRecord} の REST 直露出を回避し、
 * インフラ層の変更が API レスポンス形式に影響しないようにする。</p>
 */
public record TrackingExceptionResponse(
        String exceptionId,
        String trackingNumber,
        String exceptionType,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String responseStatus,
        String resolution,
        LocalDateTime resolvedAt,
        boolean escalated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TrackingExceptionResponse from(TrackingExceptionRecord exceptionRecord) {
        return new TrackingExceptionResponse(
                exceptionRecord.exceptionId(),
                exceptionRecord.trackingNumber(),
                exceptionRecord.exceptionType(),
                exceptionRecord.occurredAt(),
                exceptionRecord.occurredUnlocode(),
                exceptionRecord.description(),
                exceptionRecord.responseStatus(),
                exceptionRecord.resolution(),
                exceptionRecord.resolvedAt(),
                Boolean.TRUE.equals(exceptionRecord.escalated()),
                exceptionRecord.createdAt(),
                exceptionRecord.updatedAt());
    }
}
