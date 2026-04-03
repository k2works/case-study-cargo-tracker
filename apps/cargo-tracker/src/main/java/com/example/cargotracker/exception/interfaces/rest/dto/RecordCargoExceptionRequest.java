package com.example.cargotracker.exception.interfaces.rest.dto;

import com.example.cargotracker.exception.domain.model.commands.RecordCargoExceptionCommand;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 貨物例外記録リクエスト DTO。
 */
public record RecordCargoExceptionRequest(
        @NotBlank
        @Schema(description = "追跡番号", example = "TRK-AB123456")
        String trackingNumber,

        @NotNull
        @Schema(description = "例外種別（DELAY/DAMAGE/LOSS）", example = "DELAY")
        ExceptionType exceptionType,

        @Schema(description = "発生場所コード（UNLOCODE）", example = "JPTYO")
        String locationCode,

        @NotNull
        @Schema(description = "発生日時", example = "2026-05-28T10:00:00")
        LocalDateTime occurredAt,

        @Schema(description = "発生理由・状況", example = "悪天候による港湾閉鎖")
        String reason
) {
    public RecordCargoExceptionCommand toCommand() {
        return new RecordCargoExceptionCommand(trackingNumber, exceptionType, locationCode, occurredAt, reason);
    }
}
