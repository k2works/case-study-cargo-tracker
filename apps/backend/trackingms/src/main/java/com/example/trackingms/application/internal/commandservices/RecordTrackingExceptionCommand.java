package com.example.trackingms.application.internal.commandservices;

import java.time.LocalDateTime;

/**
 * 例外記録コマンド
 */
public record RecordTrackingExceptionCommand(
        String trackingNumber,
        String exceptionType,
        LocalDateTime occurredAt,
        String locationUnlocode,
        String reason,
        boolean escalationFlag,
        String damageDescription,
        String photoUrl,
        String lastKnownLocation,
        LocalDateTime lastSeenAt
) {}
