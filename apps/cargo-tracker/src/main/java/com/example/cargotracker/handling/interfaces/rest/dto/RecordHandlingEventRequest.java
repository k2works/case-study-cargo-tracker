package com.example.cargotracker.handling.interfaces.rest.dto;

import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "荷役イベント記録リクエスト")
public record RecordHandlingEventRequest(
        @NotNull(message = "予約 ID は必須です")
        UUID bookingId,
        @NotNull(message = "荷役イベント種別は必須です")
        HandlingEventType eventType,
        @NotBlank(message = "場所コードは必須です")
        String locationCode,
        @NotNull(message = "完了日時は必須です")
        LocalDateTime completionTime,
        String memo,
        @Schema(
            description = "引取確認コード。eventType が RECEIVE の場合のみ必須。他イベント種別では無視される。",
            example = "RC-20260403-001",
            nullable = true
        )
        String receiveConfirmationCode
) {
    public RecordHandlingEventCommand toCommand() {
        return new RecordHandlingEventCommand(
                bookingId, eventType, locationCode, completionTime, memo, receiveConfirmationCode);
    }
}
