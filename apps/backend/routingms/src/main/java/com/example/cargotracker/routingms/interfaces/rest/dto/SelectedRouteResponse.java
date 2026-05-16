package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SelectedRouteResponse(List<LegDto> legs) {

    public record LegDto(
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {}
}
