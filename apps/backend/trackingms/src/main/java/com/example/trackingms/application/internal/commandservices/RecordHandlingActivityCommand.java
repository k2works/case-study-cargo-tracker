package com.example.trackingms.application.internal.commandservices;

import java.time.LocalDateTime;

/**
 * 荷役作業記録コマンド
 */
public record RecordHandlingActivityCommand(
        String trackingNumber,
        String eventType,
        String locationUnlocode,
        LocalDateTime eventTime,
        String voyageNumber
) {}
