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

    public static TrackingExceptionResponse from(TrackingExceptionRecord record) {
        return new TrackingExceptionResponse(
                record.exceptionId(),
                record.trackingNumber(),
                record.exceptionType(),
                record.occurredAt(),
                record.occurredUnlocode(),
                record.description(),
                record.responseStatus(),
                record.resolution(),
                record.resolvedAt(),
                Boolean.TRUE.equals(record.escalated()),
                record.createdAt(),
                record.updatedAt());
    }
}
