package com.example.cargotracker.quote.interfaces.rest.dto;

import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommand;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積登録リクエスト DTO。
 */
@Schema(description = "見積登録リクエスト")
public record QuoteRequest(
        @Schema(description = "出発地 UN/LOCODE（例: JPTYO）", example = "JPTYO")
        @NotBlank(message = "出発地 (UN/LOCODE) は必須です")
        String originLocode,
        @Schema(description = "目的地 UN/LOCODE（例: USNYC）", example = "USNYC")
        @NotBlank(message = "目的地 (UN/LOCODE) は必須です")
        String destinationLocode,
        @Schema(description = "希望着日", example = "2025-12-01")
        @NotNull(message = "希望着日は必須です")
        LocalDate requestedArrivalDate,
        @Schema(description = "貨物種別", example = "GENERAL_CARGO")
        @NotNull(message = "貨物種別は必須です")
        CargoType cargoType,
        @Schema(description = "重量 (kg)", example = "1000.0")
        @NotNull(message = "重量は必須です")
        @DecimalMin(value = "0.01", message = "重量は 0 より大きくなければなりません")
        BigDecimal weightKg
) {
    public RegisterQuoteCommand toCommand() {
        return new RegisterQuoteCommand(
                originLocode,
                destinationLocode,
                requestedArrivalDate,
                cargoType,
                weightKg
        );
    }
}
