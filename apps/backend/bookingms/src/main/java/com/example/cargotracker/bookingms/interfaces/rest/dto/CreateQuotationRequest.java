package com.example.cargotracker.bookingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積作成リクエスト DTO（US01）。
 */
public record CreateQuotationRequest(
        @NotNull Long shipperId,
        @NotBlank String originUnLocode,
        @NotBlank String destinationUnLocode,
        @NotNull LocalDate arrivalDeadline,
        @NotBlank String cargoType,
        @NotNull @Positive BigDecimal weightKg,
        HazardInfoDto hazardInfo) {

    public record HazardInfoDto(String imoClass, String unNumber, String declaration) {
    }
}
