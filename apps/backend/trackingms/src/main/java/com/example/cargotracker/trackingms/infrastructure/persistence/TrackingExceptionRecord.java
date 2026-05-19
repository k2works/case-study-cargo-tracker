package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * tracking_exception テーブルの 1 行を表すレコード（US19）。
 */
public record TrackingExceptionRecord(
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
        LocalDateTime updatedAt) { }
