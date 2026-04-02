package com.example.cargotracker.handling.interfaces.rest.dto;

import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecordHandlingEventRequest(
        @NotNull(message = "予約 ID は必須です")
        UUID bookingId,
        @NotNull(message = "荷役イベント種別は必須です")
        HandlingEventType eventType,
        @NotBlank(message = "場所コードは必須です")
        String locationCode,
        @NotNull(message = "完了日時は必須です")
        LocalDateTime completionTime,
        String memo
) {
    public RecordHandlingEventCommand toCommand() {
        return new RecordHandlingEventCommand(bookingId, eventType, locationCode, completionTime, memo);
    }
}
