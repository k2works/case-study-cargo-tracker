package com.example.cargotracker.routingms.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航海スケジュール新規登録の REST リクエスト DTO（US24）。
 */
public record RegisterVoyageRequest(
        @NotBlank String voyageNumber,
        @NotBlank String carrierCode,
        @NotBlank String carrierName,
        @NotBlank String shipName,
        @NotBlank String originUnLocode,
        @NotBlank String destinationUnLocode,
        @NotNull LocalDateTime departureDate,
        @NotNull LocalDateTime arrivalDate,
        @NotNull @NotEmpty @Size(min = 1) @Valid List<MovementDto> carrierMovements,
        @NotNull @NotEmpty List<String> acceptedCargoTypes) {

    public record MovementDto(
            @NotBlank String departureUnLocode,
            @NotBlank String arrivalUnLocode,
            @NotNull LocalDateTime departureTime,
            @NotNull LocalDateTime arrivalTime) {
    }
}
