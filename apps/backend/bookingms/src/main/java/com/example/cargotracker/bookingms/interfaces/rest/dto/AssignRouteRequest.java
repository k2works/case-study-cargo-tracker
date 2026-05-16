package com.example.cargotracker.bookingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 経路紐付けリクエスト（US11）。
 */
public record AssignRouteRequest(List<LegDto> legs) {

    public record LegDto(
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {}
}
