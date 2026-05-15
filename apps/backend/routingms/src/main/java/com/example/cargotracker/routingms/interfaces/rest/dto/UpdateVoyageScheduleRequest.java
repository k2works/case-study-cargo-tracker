package com.example.cargotracker.routingms.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航海スケジュール更新の REST リクエスト DTO（US25 / UC19）。
 *
 * <p>Carrier と shipName は更新対象外なのでこの DTO には含めない。
 * 更新したい場合は別 Command（IT4 以降）で扱う。</p>
 */
public record UpdateVoyageScheduleRequest(
        @NotNull LocalDateTime departureDate,
        @NotNull LocalDateTime arrivalDate,
        @NotNull @NotEmpty @Size(min = 1) @Valid List<RegisterVoyageRequest.MovementDto> carrierMovements,
        @NotNull @NotEmpty List<String> acceptedCargoTypes) {
}
