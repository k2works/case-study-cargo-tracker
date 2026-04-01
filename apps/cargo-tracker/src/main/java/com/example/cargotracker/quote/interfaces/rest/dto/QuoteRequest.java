package com.example.cargotracker.quote.interfaces.rest.dto;

import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommand;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積登録リクエスト DTO。
 */
public record QuoteRequest(
        @NotBlank(message = "出発地 (UN/LOCODE) は必須です")
        String originLocode,
        @NotBlank(message = "目的地 (UN/LOCODE) は必須です")
        String destinationLocode,
        @NotNull(message = "希望着日は必須です")
        LocalDate requestedArrivalDate,
        @NotNull(message = "貨物種別は必須です")
        CargoType cargoType,
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
