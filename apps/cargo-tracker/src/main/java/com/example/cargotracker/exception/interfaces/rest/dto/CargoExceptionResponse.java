package com.example.cargotracker.exception.interfaces.rest.dto;

import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 貨物例外記録レスポンス DTO。
 */
public record CargoExceptionResponse(
        @Schema(description = "例外 ID") UUID id,
        @Schema(description = "追跡番号") String trackingNumber,
        @Schema(description = "例外種別") ExceptionType exceptionType,
        @Schema(description = "発生場所コード") String locationCode,
        @Schema(description = "発生日時") LocalDateTime occurredAt,
        @Schema(description = "理由") String reason,
        @Schema(description = "緊急フラグ") boolean urgent
) {
    public static CargoExceptionResponse from(ExceptionId id, RecordCargoExceptionRequest request, boolean urgent) {
        return new CargoExceptionResponse(
                id.value(),
                request.trackingNumber(),
                request.exceptionType(),
                request.locationCode(),
                request.occurredAt(),
                request.reason(),
                urgent
        );
    }
}
