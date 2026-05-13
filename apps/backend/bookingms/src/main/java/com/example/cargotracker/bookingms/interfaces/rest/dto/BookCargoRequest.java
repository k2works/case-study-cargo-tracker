package com.example.cargotracker.bookingms.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約登録の REST リクエスト DTO（US04）。
 */
public record BookCargoRequest(
        @NotNull Long shipperId,
        @NotNull CargoSpecDto cargoSpec,
        @NotNull RouteSpecDto routeSpec) {

    public record CargoSpecDto(
            @NotBlank String cargoType,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal weightKg,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String productName,
            @Valid DimensionsDto dimensions,
            @Valid HazardInfoDto hazardInfo,
            @Valid TemperatureConditionDto temperatureCondition) {
    }

    public record DimensionsDto(
            @NotNull @Min(0) Integer lengthCm,
            @NotNull @Min(0) Integer widthCm,
            @NotNull @Min(0) Integer heightCm) {
    }

    public record HazardInfoDto(
            @NotBlank String imoClass,
            @NotBlank String unNumber,
            @NotBlank String declaration) {
    }

    public record TemperatureConditionDto(
            @NotNull BigDecimal minCelsius,
            @NotNull BigDecimal maxCelsius) {
    }

    public record RouteSpecDto(
            @NotBlank String originUnLocode,
            @NotBlank String destinationUnLocode,
            @NotNull @Future LocalDate arrivalDeadline) {
    }
}
