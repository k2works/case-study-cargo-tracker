package com.example.cargotracker.handling.domain.model.commands;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 荷役イベント記録コマンド。
 */
public record RecordHandlingEventCommand(
        UUID bookingId,
        HandlingEventType eventType,
        String locationCode,
        LocalDateTime completionTime,
        String memo
) {
}
