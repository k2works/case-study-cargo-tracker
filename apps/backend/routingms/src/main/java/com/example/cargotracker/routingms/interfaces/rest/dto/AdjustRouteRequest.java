package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.time.LocalDate;
import java.util.List;

public record AdjustRouteRequest(
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        String cargoType,
        List<String> excludePorts
) {}
